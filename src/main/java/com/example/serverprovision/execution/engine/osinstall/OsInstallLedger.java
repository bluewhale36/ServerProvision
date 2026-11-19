package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OS 설치 원장의 계열 공통부(E4-1-b-4-1). 계열마다 같은 step(OS_INSTALLING) 에 행을 쓰므로 조회는 언제나 meta 의
 * {@code origin} 으로 거른다 — 거르지 않으면 한 계열의 스위퍼 · 재진입 판정이 다른 계열의 열린 행을 집는다.
 * RUNNING 행 하나가 서빙 한 사이클이고, 실패는 그 행을 사유와 함께 닫는다(서빙 meta 보존).
 */
public abstract class OsInstallLedger {

    /** 서빙 시각부터 설치 시한이 지난 뒤의 재진입. */
    public static final String INSTALL_TIMEOUT = "INSTALL_TIMEOUT";
    /** 재진입 횟수가 상한을 넘었다 — 재PXE 루프. */
    public static final String REPXE_LOOP = "REPXE_LOOP";
    /** 운영자 수동 실패 전환이 열린 서빙 행을 닫았다. */
    public static final String OPERATOR = "OPERATOR";
    /** 새 서빙이 시작될 때 아직 열려 있던 옛 행을 닫았다. */
    public static final String SUPERSEDED = "SUPERSEDED";
    /** 완료 보고가 서빙 행을 SUCCEEDED 로 닫았다. */
    public static final String COMPLETED = "COMPLETED";

    protected static final ProvisioningPhaseStep STEP = ProvisioningPhaseStep.OS_INSTALLING;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    protected final ProvisioningHistoryRecorder recorder;
    protected final ProvisioningHistoryRepository historyRepository;
    protected final ObjectMapper objectMapper;

    protected OsInstallLedger(ProvisioningHistoryRecorder recorder, ProvisioningHistoryRepository historyRepository,
                              ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    /** 이 원장이 meta 에 적는 계열 표지. */
    public abstract OsInstallOrigin origin();

    /** 이 원장이 적은 행인가(meta 의 origin 이 이 계열). 표지 없는 행 · 다른 표지(운영자 전환 · 게스트 보고)는 아니다. */
    public boolean owns(ProvisioningHistory row) {
        return origin().matches(read(row).get("origin"));
    }

    /**
     * 지금 열려 있는 이 계열의 서빙 행 — 상태 조건으로 직접 묻고 origin 으로 거른다. 열린 OS 설치 행은 계열을 통틀어
     * 언제나 하나라(새 서빙이 옛 행을 SUPERSEDED 로 닫는다) 최신 RUNNING 행 하나만 보면 된다.
     */
    public Optional<ProvisioningHistory> latestRunning(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guestServerId, STEP, ProvisioningStatus.RUNNING).filter(this::owns);
    }

    /**
     * 가장 최근 OS 설치 행(상태 · 계열 무관 — 운영자 전환 행도 포함). 호출자가 {@link #owns} 나 사유로 판정한다
     * (완료 보고의 멱등 판정이 "최신 행이 완료 행인가" 를 이 값으로 본다).
     */
    public Optional<ProvisioningHistory> latestOf(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(guestServerId, STEP);
    }

    /** 재진입 서빙 — 행 교체가 아니라 meta 갱신(served 는 그대로, reentries +1). */
    public int bumpReentry(ProvisioningHistory row, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        int next = intOf(meta, "reentries") + 1;
        meta.put("reentries", next);
        meta.put("lastReentryAt", now.toString());
        row.updateRunningMeta(write(meta));
        return next;
    }

    /**
     * 시한 · 상한 초과 — 열린 행을 사유와 함께 닫고 실패로 전환한다. 서빙 meta 는 보존한다. 열린 행이 없으면(비정상)
     * 단발 기록으로 남긴다.
     */
    public void failRunning(GuestServer server, ProvisioningProgress progress, ProvisioningHistory row,
                            String reason, String detail, LocalDateTime now) {
        if (row == null || !abortRunning(row, reason, detail, now)) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("origin", origin().wire());
            meta.put("reason", reason);
            meta.put("detail", detail == null ? "" : detail);
            recorder.recordInstant(server, STEP, ProvisioningStatus.FAILED, write(meta), now);
        }
        progress.markFailed(now);
    }

    /** 열린 서빙 행을 사유와 함께 FAILED 로 닫는다 — 진행 신호는 건드리지 않는다. 이미 닫힌 행이면 false. */
    public boolean abortRunning(ProvisioningHistory row, String reason, String detail, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        meta.putIfAbsent("origin", origin().wire());
        meta.put("reason", reason);
        meta.put("detail", detail == null ? "" : detail);
        return row.close(ProvisioningStatus.FAILED, write(meta), now);
    }

    public LocalDateTime servedAtOf(ProvisioningHistory row) {
        Object v = read(row).get("served");
        return v == null ? null : LocalDateTime.parse(v.toString());
    }

    public int reentriesOf(ProvisioningHistory row) {
        return intOf(read(row), "reentries");
    }

    /** 실패 · 완료 행의 사유 코드 — 없으면 null. */
    public String reasonOf(ProvisioningHistory row) {
        Object v = read(row).get("reason");
        return v == null ? null : v.toString();
    }

    protected static int intOf(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    protected Map<String, Object> read(ProvisioningHistory row) {
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

    protected String write(Map<String, Object> meta) {
        return objectMapper.writeValueAsString(meta);
    }
}
