package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Windows 설치 원장(E4-1-a-3) — RUNNING 행 하나가 서빙 한 사이클이다. 열 때 이미지 · 서빙 시각 · 재진입 0 을 적고,
 * 재진입마다 같은 행의 meta 를 덧쓰며({@code updateRunningMeta}, E3-1 관용구), 시한 · 상한 초과는 <b>그 행을 사유와
 * 함께 닫는다</b>(별도 instant 행이 아니다 — 열린 행이 영원히 남지 않게, 재시도 뒤의 {@link #latestRunning} 이 옛 행을
 * 집지 않게). 완료 보고(E4-1-a-4)는 같은 행을 SUCCEEDED 로 닫으며 서빙 meta 를 보존한다. 실패는 예외가 아니라 원장 사유
 * 코드다(E3 SettingLedger · RaidLedger 관례). meta 에 비밀값 · 토큰은 없다.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallLedger {

    public static final String ORIGIN = "windows-install";
    /** 서빙 시각부터 설치 시한이 지난 뒤의 재진입. */
    public static final String INSTALL_TIMEOUT = "INSTALL_TIMEOUT";
    /** 재진입 횟수가 상한을 넘었다 — 재PXE 루프. */
    public static final String REPXE_LOOP = "REPXE_LOOP";
    /** 운영자 수동 실패 전환이 열린 서빙 행을 닫았다(CP5 F-1). */
    public static final String OPERATOR = "OPERATOR";
    /** 새 서빙이 시작될 때 아직 열려 있던 옛 행을 닫았다(정정 전 데이터의 자가 치유). */
    public static final String SUPERSEDED = "SUPERSEDED";
    /** 첫 로그온 완료 보고가 서빙 행을 SUCCEEDED 로 닫았다(E4-1-a-4). */
    public static final String COMPLETED = "COMPLETED";

    private static final ProvisioningPhaseStep STEP = ProvisioningPhaseStep.OS_INSTALLING;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ProvisioningHistoryRecorder recorder;
    private final ProvisioningHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    /** 첫 서빙 — 스크립트를 내준 사실이 착수다(D-1). */
    public ProvisioningHistory openServed(GuestServer server, WindowsImageName image, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", ORIGIN);
        meta.put("image", image == null ? null : image.value());
        meta.put("served", now.toString());
        meta.put("reentries", 0);
        return recorder.openRunning(server, STEP, now, write(meta));
    }

    /** 재진입 서빙 — 행 교체가 아니라 meta 갱신(served 는 그대로, reentries +1). */
    public int bumpReentry(ProvisioningHistory row, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        int next = reentriesOf(meta) + 1;
        meta.put("reentries", next);
        meta.put("lastReentryAt", now.toString());
        row.updateRunningMeta(write(meta));
        return next;
    }

    /**
     * 시한 · 상한 초과 — 열린 행을 사유와 함께 닫고 실패로 전환한다. 서빙 메타(image · served · reentries)는 보존한다
     * ([[원장 close 가 메타를 덮는다]] 교훈). 열린 행이 없으면(비정상) 단발 기록으로 남긴다.
     */
    public void failRunning(GuestServer server, ProvisioningProgress progress, ProvisioningHistory row,
                            String reason, String detail, LocalDateTime now) {
        if (row == null || !abortRunning(row, reason, detail, now)) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("origin", ORIGIN);
            meta.put("reason", reason);
            meta.put("detail", detail == null ? "" : detail);
            recorder.recordInstant(server, STEP, ProvisioningStatus.FAILED, write(meta), now);
        }
        progress.markFailed(now);
    }

    /**
     * 열린 서빙 행을 사유와 함께 FAILED 로 닫는다 — 진행 신호는 건드리지 않는다(이미 실패 전환된 뒤의 뒷정리 · CP5 F-1).
     * 서빙 meta 는 보존한다. 이미 닫힌 행이면 false.
     */
    public boolean abortRunning(ProvisioningHistory row, String reason, String detail, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        meta.putIfAbsent("origin", ORIGIN);
        meta.put("reason", reason);
        meta.put("detail", detail == null ? "" : detail);
        return row.close(ProvisioningStatus.FAILED, write(meta), now);
    }

    /** 완료 보고가 실은 사실(E4-1-a-4) — 비밀값 · 토큰은 없다. 로그 꼬리는 드라이버 0 의 이유를 원장에서 읽기 위한 것. */
    public record Completion(String computerName, String osVersion, int driversAdded, int problemDeviceCount,
                             List<String> problemDevices, String setupCompleteLogTail) {
    }

    /**
     * 완료 보고 — 열린 서빙 행을 SUCCEEDED 로 닫되 서빙 meta(image · served · reentries)를 보존하고 완료 meta 를 더한다
     * (-3 인계 ③). 이미 닫힌 행이면 false.
     */
    public boolean closeSucceeded(ProvisioningHistory row, Completion c, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        meta.putIfAbsent("origin", ORIGIN);
        meta.put("completedAt", now.toString());
        meta.put("computerName", c.computerName());
        meta.put("osVersion", c.osVersion());
        meta.put("driversAdded", c.driversAdded());
        meta.put("problemDeviceCount", c.problemDeviceCount());
        meta.put("problemDevices", c.problemDevices() == null ? List.of() : c.problemDevices());
        if (c.setupCompleteLogTail() != null && !c.setupCompleteLogTail().isBlank()) {
            meta.put("setupCompleteLogTail", c.setupCompleteLogTail());
        }
        meta.put("reason", COMPLETED);
        meta.put("detail", "설치 완료 · 드라이버 " + c.driversAdded() + " · 문제 장치 " + c.problemDeviceCount());
        return row.close(ProvisioningStatus.SUCCEEDED, write(meta), now);
    }

    /**
     * 지금 열려 있는 서빙 행 — 상태 조건으로 직접 묻는다. "최신 행을 집어 RUNNING 인지 보는" 판독은 운영자 실패 전환처럼
     * 뒤에 다른 행이 쌓이는 순간 열린 행을 놓쳤다(CP5 F-1 재발의 원인). 닫힌 행(실패 · 종결)만 있으면 empty.
     */
    public Optional<ProvisioningHistory> latestRunning(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guestServerId, STEP, ProvisioningStatus.RUNNING);
    }

    public Optional<ProvisioningHistory> latestOf(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(guestServerId, STEP);
    }

    public LocalDateTime servedAtOf(ProvisioningHistory row) {
        Object v = read(row).get("served");
        return v == null ? null : LocalDateTime.parse(v.toString());
    }

    public int reentriesOf(ProvisioningHistory row) {
        return reentriesOf(read(row));
    }

    public String imageOf(ProvisioningHistory row) {
        Object v = read(row).get("image");
        return v == null ? null : v.toString();
    }

    /** 실패 행의 사유 코드 — 이 원장이 적은 행이 아니면 null. */
    public String reasonOf(ProvisioningHistory row) {
        Object v = read(row).get("reason");
        return v == null ? null : v.toString();
    }

    public boolean isWindowsInstallRow(ProvisioningHistory row) {
        return ORIGIN.equals(read(row).get("origin"));
    }

    /** 완료 보고로 닫힌 행인가 — 멱등 판정(중복 보고 no-op)과 카드의 완료 분기가 같은 판독을 쓴다. */
    public boolean isCompletedRow(ProvisioningHistory row) {
        return row.getStatus() == ProvisioningStatus.SUCCEEDED && COMPLETED.equals(reasonOf(row));
    }

    public LocalDateTime completedAtOf(ProvisioningHistory row) {
        Object v = read(row).get("completedAt");
        return v == null ? null : LocalDateTime.parse(v.toString());
    }

    public String computerNameOf(ProvisioningHistory row) {
        return stringOf(row, "computerName");
    }

    public String osVersionOf(ProvisioningHistory row) {
        return stringOf(row, "osVersion");
    }

    public int driversAddedOf(ProvisioningHistory row) {
        return intOf(read(row), "driversAdded");
    }

    public int problemDeviceCountOf(ProvisioningHistory row) {
        return intOf(read(row), "problemDeviceCount");
    }

    public List<String> problemDevicesOf(ProvisioningHistory row) {
        Object v = read(row).get("problemDevices");
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return List.copyOf(out);
    }

    private String stringOf(ProvisioningHistory row, String key) {
        Object v = read(row).get(key);
        return v == null ? null : v.toString();
    }

    private static int intOf(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static int reentriesOf(Map<String, Object> meta) {
        return intOf(meta, "reentries");
    }

    private Map<String, Object> read(ProvisioningHistory row) {
        String raw = row.getStatusMeta();
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(raw, MAP));
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    private String write(Map<String, Object> meta) {
        return objectMapper.writeValueAsString(meta);
    }
}
