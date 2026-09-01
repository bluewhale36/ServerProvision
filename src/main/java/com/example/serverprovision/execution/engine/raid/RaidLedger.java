package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * RAID 구성 원장(E3.5-1 → E3.5-3 확장) — E3 {@code SettingLedger} 관례: 실패는 예외가 아니라 원장 사유
 * 코드다. 에이전트도 같은 코드 문자열을 쓴다(TOOL_MISSING · CREATE_REJECTED — agent.sh 계약 주석 참조).
 * 보류(PENDING)와 실패(FAILED)를 구분한다 — 보류는 정의서 수정 · 축 도입으로 저절로 풀리는 상태다.
 */
@Component
@RequiredArgsConstructor
public class RaidLedger {

    /** 정의서가 지정한 카드와 감지 카드의 PCI Subsystem 이 다르다. */
    public static final String CARD_MISMATCH = "CARD_MISMATCH";
    /** 정의서가 카드를 지정했는데 게스트에서 RAID 카드를 감지하지 못했다. */
    public static final String CARD_NOT_DETECTED = "CARD_NOT_DETECTED";
    /** 진단 환경에 계열 CLI 가 없거나 실행에 실패했다 — 에이전트가 FAILED close 에 싣는다. */
    public static final String TOOL_MISSING = "TOOL_MISSING";
    /** 보고 원문을 해석하지 못했다 — 원문은 원장 statusMeta 가 보존한다. */
    public static final String REPORT_UNPARSABLE = "REPORT_UNPARSABLE";
    /** 집행 지시 직전의 계획 동결(실패 아님) — 검증의 대조 기준(E3.5-3 결정 2). */
    public static final String PLANNED = "PLANNED";
    /** 외부 기존 볼륨 존재 + 보존 · 파괴 축 미도입 — 보류(E3.5-3 결정 3). */
    public static final String POLICY_UNDECIDED = "POLICY_UNDECIDED";
    /** 계획 산출이 거절됐다(VOLUME_LIMIT 등) — 보류(정의서 수정으로 해소). */
    public static final String PLAN_REJECTED = "PLAN_REJECTED";
    /** 어댑터 CLI 실행 실패 — 에이전트가 FAILED close 에 로그 원문과 함께 싣는다. */
    public static final String CREATE_REJECTED = "CREATE_REJECTED";
    /** 재채집 결과가 동결 계획과 다르다(E3.5-3 결정 4). */
    public static final String RESULT_MISMATCH = "RESULT_MISMATCH";
    /** 명시한 보존 정책과 외부 기존 볼륨의 모순(E3.5-4 결정 3 · D-7) — 보류가 아니라 실패. */
    public static final String EXISTING_CONFIG = "EXISTING_CONFIG";

    private final ProvisioningHistoryRecorder recorder;
    private final ProvisioningHistoryRepository historyRepository;

    /**
     * 서버 판정 실패의 단발 기록 + 실패 신호 — 에이전트의 close(SUCCEEDED)와 별개 행이다
     * (수집은 성공했으나 대조 · 검증이 거절한 사실을 원장이 구분해 남긴다).
     */
    public void failInstant(GuestServer server, ProvisioningProgress progress, ProvisioningPhaseStep stepCode,
                            String reason, String detail, LocalDateTime now) {
        recorder.recordInstant(server, stepCode, ProvisioningStatus.FAILED, reasonJson(reason, detail), now);
        progress.markFailed(now);
    }

    /**
     * 보류의 단발 기록(PENDING · 실패 신호 없음) — 같은 사유가 최신 행이면 남기지 않는다(중복 억제:
     * 게스트가 30초마다 체크인하므로 억제가 없으면 보류 게스트 하나가 같은 행을 무한히 쌓는다).
     */
    public void holdInstant(GuestServer server, ProvisioningPhaseStep stepCode,
                            String reason, String detail, LocalDateTime now) {
        Optional<ProvisioningHistory> latest = latestOf(server.getId(), stepCode);
        if (latest.filter(h -> h.getStatus() == ProvisioningStatus.PENDING
                && h.getStatusMeta() != null
                && h.getStatusMeta().contains("\"reason\":\"" + reason + "\"")).isPresent()) {
            return;
        }
        recorder.recordInstant(server, stepCode, ProvisioningStatus.PENDING, reasonJson(reason, detail), now);
    }

    /**
     * 집행 직전의 계획 동결(E3.5-3 결정 2) — PENDING 단발 행에 계획 JSON 을 싣는다. RUNNING 행 statusMeta
     * 에 두지 않는 이유: 원장 close 가 메타를 결과로 덮는다(E2-2 F-1 실측 교훈). 최신 행이 이미 동결
     * (PENDING · PLANNED)이거나 집행 중(RUNNING)이면 다시 쌓지 않는다 — 응답 유실 재체크인의 멱등.
     */
    public void freezePlanned(GuestServer server, String planJson, LocalDateTime now) {
        Optional<ProvisioningHistory> latest = latestOf(server.getId(), ProvisioningPhaseStep.RAID_APPLYING);
        boolean frozenOrRunning = latest.filter(h ->
                h.getStatus() == ProvisioningStatus.RUNNING
                        || (h.getStatus() == ProvisioningStatus.PENDING
                        && h.getStatusMeta() != null
                        && h.getStatusMeta().contains("\"reason\":\"" + PLANNED + "\""))).isPresent();
        if (frozenOrRunning) {
            return;
        }
        recorder.recordInstant(server, ProvisioningPhaseStep.RAID_APPLYING, ProvisioningStatus.PENDING,
                "{\"reason\":\"" + PLANNED + "\",\"plan\":" + planJson + "}", now);
    }

    /** 최신 동결 계획의 statusMeta 원문 — 검증(결정 4)과 payload 파생이 같은 SSOT 를 읽는다. */
    public Optional<String> latestFrozenPlanMeta(java.util.UUID guestServerId) {
        return historyRepository.findAllByServerIdOrderByStartedAt(guestServerId).stream()
                .filter(h -> h.getStepCode() == ProvisioningPhaseStep.RAID_APPLYING)
                .filter(h -> h.getStatusMeta() != null
                        && h.getStatusMeta().contains("\"reason\":\"" + PLANNED + "\""))
                .reduce((first, second) -> second)   // 시작 시각 오름차순의 마지막 = 최신 동결
                .map(ProvisioningHistory::getStatusMeta);
    }

    /** step 별 최신 행 — 지시 판정(RAID_VERIFY 등)과 중복 억제가 함께 쓴다. */
    public Optional<ProvisioningHistory> latestOf(java.util.UUID guestServerId, ProvisioningPhaseStep stepCode) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(guestServerId, stepCode);
    }

    private String reasonJson(String reason, String detail) {
        return "{\"reason\":\"" + reason + "\",\"detail\":\"" + escape(detail) + "\"}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
