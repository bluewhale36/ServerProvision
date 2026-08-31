package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * RAID 구성 원장(E3.5-1) — E3 {@code SettingLedger} 관례: 실패는 예외가 아니라 원장 사유 코드다.
 * 에이전트도 같은 코드 문자열을 쓴다(TOOL_MISSING — agent.sh 계약 주석 참조).
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

    private final ProvisioningHistoryRecorder recorder;

    /**
     * 서버 판정 실패의 단발 기록 + 실패 신호 — 에이전트의 close(SUCCEEDED)와 별개 행이다
     * (수집은 성공했으나 대조가 거절한 사실을 원장이 구분해 남긴다).
     */
    public void failInstant(GuestServer server, ProvisioningProgress progress,
                            String reason, String detail, LocalDateTime now) {
        recorder.recordInstant(server, ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING,
                ProvisioningStatus.FAILED, "{\"reason\":\"" + reason + "\",\"detail\":\"" + escape(detail) + "\"}", now);
        progress.markFailed(now);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
