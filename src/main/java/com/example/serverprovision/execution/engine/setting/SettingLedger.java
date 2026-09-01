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
 * 설정 적용 원장(E3-1 D-5 · E3-2 D-9) — BIOS_SETTING · BMC_SETTING 행의 열림 · 관찰 덧쓰기 · 종결. 목표는 열 때 적고 닫을 때도
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
    /** 채집한 레지스트리의 허용값 밖 목표 — PATCH 전에 닫는다(E3-3 R6). 운영자가 할 일 = 템플릿 정정. */
    public static final String VALUE_NOT_IN_REGISTRY = "VALUE_NOT_IN_REGISTRY";
    public static final String READBACK_MISMATCH = "READBACK_MISMATCH";
    public static final String RETURN_TIMEOUT = "RETURN_TIMEOUT";
    public static final String IDENTITY_MISMATCH = "IDENTITY_MISMATCH";
    public static final String BMC_UNREACHABLE = "BMC_UNREACHABLE";
    /** E3-2 — BMC 가 항목 쓰기를 거절(데이터 · 프로토콜). detail 에 항목명 · code. */
    public static final String WRITE_REJECTED = "WRITE_REJECTED";
    /** E3-2 — 자격증명 후보가 전부 거부됨(E1.6 표준화가 안 된 장비). */
    public static final String AUTH_REJECTED = "AUTH_REJECTED";

    private final ProvisioningHistoryRecorder recorder;
    private final ObjectMapper objectMapper;

    /** 착수 — 되돌리기 어려운 조작(PATCH)보다 먼저 연다. rebootAt · pendingSeen 은 아직 없다. */
    public ProvisioningHistory open(GuestServer server, BiosSettingTarget target, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", "setting");
        meta.put("axis", SettingAxis.BIOS.name());
        meta.put("target", target.attributes());
        return recorder.openRunning(server, ProvisioningPhaseStep.BIOS_SETTING, now, write(meta));
    }

    /** BMC 축 착수(E3-2 D-9) — 첫 쓰기보다 먼저 연다. 항목별 결과는 {@code items} 에 누적된다. */
    public ProvisioningHistory openBmc(GuestServer server, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", "setting");
        meta.put("axis", SettingAxis.BMC.name());
        meta.put("items", new LinkedHashMap<String, Object>());
        return recorder.openRunning(server, ProvisioningPhaseStep.BMC_SETTING, now, write(meta));
    }

    /** 항목 결과 덧쓰기 — 쓴 직후마다 남겨 크래시가 나도 어디까지 갔는지 원장이 안다. */
    public void markItem(ProvisioningHistory row, BmcSettingItem item, BmcItemOutcome outcome) {
        Map<String, Object> meta = read(row);
        Map<String, Object> items = itemsMap(meta);
        items.put(item.name(), outcome.wire());
        meta.put("items", items);
        row.updateRunningMeta(write(meta));
    }

    /** Bond 를 썼고 되읽기 전에 연결이 끊겼다 — 이 시각부터 재접속 시한을 잰다(D-8). */
    public void markBondAt(ProvisioningHistory row, LocalDateTime at) {
        Map<String, Object> meta = read(row);
        meta.put("bondAt", at.toString());
        row.updateRunningMeta(write(meta));
    }

    public LocalDateTime bondAtOf(ProvisioningHistory row) {
        Object v = read(row).get("bondAt");
        return v == null ? null : LocalDateTime.parse(v.toString());
    }

    public Map<String, String> itemsOf(ProvisioningHistory row) {
        Map<String, String> out = new LinkedHashMap<>();
        itemsMap(read(row)).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    /** 종결 detail — "3개 적용 · FAN_PROFILE 건너뜀(NO_FAN_PROFILE …)". */
    public String summaryOf(ProvisioningHistory row) {
        Map<String, String> items = itemsOf(row);
        long applied = items.values().stream().filter(v -> v.startsWith(BmcItemOutcome.Status.APPLIED.name())).count();
        StringBuilder detail = new StringBuilder(applied + "개 적용");
        items.forEach((name, wire) -> {
            if (wire.startsWith(BmcItemOutcome.Status.SKIPPED.name())) {
                int colon = wire.indexOf(':');
                detail.append(" · ").append(name).append(" 건너뜀").append(colon > 0 ? "(" + wire.substring(colon + 1) + ")" : "");
            }
        });
        return detail.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> itemsMap(Map<String, Object> meta) {
        Object v = meta.get("items");
        return v instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
    }

    /** pending 관찰 — 실패 판정이 아니라 기록이다(D-4 개정 2). */
    public void markPending(ProvisioningHistory row, boolean seen) {
        Map<String, Object> meta = read(row);
        meta.put("pendingSeen", seen);
        row.updateRunningMeta(write(meta));
    }

    /** 재부팅을 걸었다 — 이 시각 이후의 게스트 접촉이 복귀 신호다. */
    public void markRebooted(ProvisioningHistory row, LocalDateTime at) {
        markRebooted(row, at, null);
    }

    /** 무장 요약을 함께 남기는 변형(E2-4 Q4) — 별도 행 대신 이미 열린 행의 meta 가 사건을 든다. */
    public void markRebooted(ProvisioningHistory row, LocalDateTime at, String armSummary) {
        Map<String, Object> meta = read(row);
        meta.put("rebootAt", at.toString());
        if (armSummary != null && !armSummary.isBlank()) {
            meta.put("arm", armSummary);
        }
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

    /** 착수 전 판정(목표 없음 · BMC 없음) — 열린 행이 없으므로 그 축의 step 에 단발 기록. */
    public void instant(GuestServer server, ProvisioningPhaseStep step, ProvisioningStatus status, String reason,
                        String detail, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", reason);
        SettingAxis.of(step).ifPresent(axis -> meta.put("axis", axis.name()));
        meta.put("detail", detail == null ? "" : detail);
        recorder.recordInstant(server, step, status, write(meta), now);
    }

    /**
     * 커서 자리에 실패를 적고 진행을 실패로 전환한다(ES-2 D-5 규약 — E2-2 FlashLedger 와 같은 결). step 을 커서에서
     * 읽는 이유: 두 축이 같은 원장을 쓰므로 BIOS_SETTING 으로 못 박으면 BMC 축의 실패가 엉뚱한 자리에 남는다(E3-2 테스트 발견).
     */
    public void failAtCursor(GuestServer server, ProvisioningProgress progress, String reason, String detail,
                             LocalDateTime now) {
        instant(server, progress.getCurrentStep(), ProvisioningStatus.FAILED, reason, detail, now);
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
