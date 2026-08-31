package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 집행이 원장에 남기는 방식(E2-2) — 여러 행이 같은 모양의 기록을 남기므로 그 조립을 한자리에 모은다.
 *
 * <p>사유 어휘를 나누는 것은 <b>운영자가 할 일이 다르기</b> 때문이다 — 파일을 볼지, 장비 상태를 볼지,
 * 네트워크를 볼지, 주소가 어긋난 원인을 급히 찾을지가 사유마다 갈린다. 어휘를 뭉치면 원장을 읽고도
 * 어디부터 봐야 할지 알 수 없다.</p>
 */
@Component
@RequiredArgsConstructor
public class FlashLedger {

    /** 사유 어휘 — 작성과 판독이 이 상수들을 공유한다. */
    public static final String FLASH_COMPLETED = "flash-completed";
    public static final String FLASH_EXCEPTION = "flash-exception";
    public static final String FLASH_TIMEOUT = "flash-timeout";
    public static final String RETURN_TIMEOUT = "return-timeout";
    public static final String VERIFY_MISMATCH = "verify-mismatch";
    public static final String IDENTITY_MISMATCH = "bmc-identity-mismatch";
    public static final String BMC_UNREACHABLE = "bmc-unreachable";
    public static final String RESOLVE_SKIPPED = "resolve-skipped";
    /** 전원 차단 사건(E2-4 Q4) — 되돌릴 수 없는 일회 사건이라 원장에 남긴다. */
    public static final String POWER_OFF = "power-off";
    /** 전원 투입 사건(E2-4 Q4) — detail 에 무장(BootSourceOverride) 결과가 함께 실린다. */
    public static final String POWER_ON = "power-on";

    /**
     * 축이 아니라 <b>phase 수준에서 일어난 사건</b>의 사유들. 기록은 "실패 지점 = 커서" 규약(ES-2 D-5)에
     * 따라 커서 step 자리에 남지만, 그것이 <b>그 축의 결과는 아니다</b> — 화면이 이 구분을 못 하면
     * 이미 성공한 축이 실패로 뒤집혀 보인다(CP5 F-2). 전원 사건 행(E2-4)도 같은 자리라 여기 든다 —
     * 이 목록에 없으면 축 처리 판정({@code FlashContext.closedRowOf})까지 오염된다.
     */
    private static final java.util.Set<String> PHASE_LEVEL_REASONS =
            java.util.Set.of(RETURN_TIMEOUT, IDENTITY_MISMATCH, BMC_UNREACHABLE, POWER_OFF, POWER_ON);

    /** 이 사유가 축의 결과인가, 아니면 phase 수준 사건인가. */
    public static boolean isPhaseLevel(String reason) {
        return reason != null && PHASE_LEVEL_REASONS.contains(reason);
    }

    private final ProvisioningHistoryRecorder recorder;

    /** 굽기 시작 — 무엇을 어느 Task 로 굽는지를 여는 시점에 함께 적는다(D-4). 이름은 표시용(E2-4 R7). */
    public void openFlash(GuestServer server, FirmwareAxis axis, AxisResolution decided,
                          String taskPath, LocalDateTime now) {
        recorder.openRunning(server, axis.getStep(), now,
                ProvisioningHistory.flashTargetMeta(decided.resourceName(), decided.display(),
                        decided.firmwareId(), taskPath));
    }

    /**
     * 전원 사건의 단발 기록(E2-4 Q4) — 커서 축 step 자리에 남고 사유 코드가 구분한다. 축 결과가
     * 아니므로 {@link #isPhaseLevel} 이 가린다. detail 에는 전원 결과 메시지(무장 요약 포함)가 실린다.
     */
    public void instantPower(GuestServer server, com.example.serverprovision.execution.enums.ProvisioningPhaseStep step,
                             String reason, String detail, LocalDateTime now) {
        recorder.recordInstant(server, step, ProvisioningStatus.SUCCEEDED,
                ProvisioningHistory.flashOutcomeMeta(reason, detail), now);
    }

    /** 굽지 않고 지나가는 축 — 그 사실만 남긴다. */
    public void skipAxis(GuestServer server, FirmwareAxis axis, String why, LocalDateTime now) {
        recorder.recordInstant(server, axis.getStep(), ProvisioningStatus.SKIPPED,
                ProvisioningHistory.flashOutcomeMeta(RESOLVE_SKIPPED, why), now);
    }

    /**
     * 열린 굽기 행을 결과로 닫는다 — 목표와 Task 경로는 <b>지우지 않고 함께 남긴다.</b> 재부팅 뒤
     * 반영 확인이 대조할 기준이 그 기록이기 때문이다(D-4).
     */
    public void close(ProvisioningHistory row, ProvisioningStatus status, String reason,
                      String detail, LocalDateTime now) {
        row.closeFlash(status, reason, detail, now);
    }

    /**
     * 커서 자리에 실패를 적는다 — phase 이름으로 된 원장 행은 만들 수 없고(step enum 에 그 상수가 없다),
     * "실패 지점 = 커서" 라는 규약이 이미 있다(ES-2 D-5). 재시도 차단도 커서를 사실로 쓴다.
     */
    public void failAtCursor(GuestServer server, ProvisioningProgress progress,
                             String reason, String detail, LocalDateTime now) {
        recorder.recordInstant(server, progress.getCurrentStep(), ProvisioningStatus.FAILED,
                ProvisioningHistory.flashOutcomeMeta(reason, detail), now);
        progress.markFailed(now);
    }

    /** 그 축 자리에 실패를 적는다. */
    public void failAxis(GuestServer server, ProvisioningProgress progress, FirmwareAxis axis,
                         String reason, String detail, LocalDateTime now) {
        recorder.recordInstant(server, axis.getStep(), ProvisioningStatus.FAILED,
                ProvisioningHistory.flashOutcomeMeta(reason, detail), now);
        progress.markFailed(now);
    }
}
