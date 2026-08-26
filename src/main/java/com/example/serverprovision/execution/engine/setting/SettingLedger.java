package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 설정 적용 원장(E3-1 D-5) — BIOS_SETTING 행의 열림 · 관찰 덧쓰기 · 종결. 목표는 열 때 적고 닫을 때도
 * <b>지우지 않는다</b>(E2-2 F-1 교훈). 목표가 맵이라 JSON 으로 다루며, 사유 어휘를 나누는 것은 운영자가
 * 사유마다 볼 곳이 다르기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class SettingLedger {

    public static final String APPLIED = "APPLIED";
    public static final String NO_TARGET = "NO_TARGET";
    public static final String BMC_REQUIRED = "BMC_REQUIRED";
    public static final String PATCH_REJECTED = "PATCH_REJECTED";
    public static final String READBACK_MISMATCH = "READBACK_MISMATCH";
    public static final String RETURN_TIMEOUT = "RETURN_TIMEOUT";
    public static final String IDENTITY_MISMATCH = "IDENTITY_MISMATCH";
    public static final String BMC_UNREACHABLE = "BMC_UNREACHABLE";

    private final ProvisioningHistoryRecorder recorder;
    private final ObjectMapper objectMapper;

    /** 착수 — 되돌리기 어려운 조작(PATCH)보다 먼저 연다. rebootAt · pendingSeen 은 아직 없다. */
    public ProvisioningHistory open(GuestServer server, BiosSettingTarget target, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", "setting");
        meta.put("target", target.attributes());
        return recorder.openRunning(server, ProvisioningPhaseStep.BIOS_SETTING, now, write(meta));
    }

    /** pending 관찰 — 실패 판정이 아니라 기록이다(D-4 개정 2). */
    public void markPending(ProvisioningHistory row, boolean seen) {
        Map<String, Object> meta = read(row);
        meta.put("pendingSeen", seen);
        row.updateRunningMeta(write(meta));
    }

    /** 재부팅을 걸었다 — 이 시각 이후의 게스트 접촉이 복귀 신호다. */
    public void markRebooted(ProvisioningHistory row, LocalDateTime at) {
        Map<String, Object> meta = read(row);
        meta.put("rebootAt", at.toString());
        row.updateRunningMeta(write(meta));
    }

    /** 열린 행을 결과로 닫는다 — target · rebootAt · pendingSeen 을 보존한 채 사유를 덧쓴다. */
    public void close(ProvisioningHistory row, ProvisioningStatus status, String reason, String detail,
                      LocalDateTime now) {
        Map<String, Object> meta = read(row);
        meta.put("origin", reason);
        meta.put("detail", detail == null ? "" : detail);
        row.close(status, write(meta), now);
    }

    /** 착수 전 판정(목표 없음 · BMC 없음) — 열린 행이 없으므로 단발 기록. */
    public void instant(GuestServer server, ProvisioningStatus status, String reason, String detail,
                        LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", reason);
        meta.put("detail", detail == null ? "" : detail);
        recorder.recordInstant(server, ProvisioningPhaseStep.BIOS_SETTING, status, write(meta), now);
    }

    /** 커서 자리에 실패를 적고 진행을 실패로 전환한다(ES-2 D-5 규약 — E2-2 FlashLedger 와 같은 결). */
    public void failAtCursor(GuestServer server, ProvisioningProgress progress, String reason, String detail,
                             LocalDateTime now) {
        instant(server, ProvisioningStatus.FAILED, reason, detail, now);
        progress.markFailed(now);
    }

    public LocalDateTime rebootAtOf(ProvisioningHistory row) {
        Object v = read(row).get("rebootAt");
        return v == null ? null : LocalDateTime.parse(v.toString());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> targetOf(ProvisioningHistory row) {
        Object v = read(row).get("target");
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private Map<String, Object> read(ProvisioningHistory row) {
        String raw = row.getStatusMeta();
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        JsonNode node = objectMapper.readTree(raw);
        Map<String, Object> map = objectMapper.convertValue(node, Map.class);
        return new LinkedHashMap<>(map);
    }

    private String write(Map<String, Object> meta) {
        return objectMapper.writeValueAsString(meta);
    }
}
