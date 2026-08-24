package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 굽기 작업의 진행 상태(E2-2) — Redfish {@code TaskService} 응답의 {@code TaskState} 를 우리 어휘로
 * 정규화한 것이다. 실측(E0-4-2)에서 관측된 전이는 {@code New → Running → Completed} 이고, 실패는
 * {@code Exception} 으로 떨어지며 그때 {@code Messages[].Severity} 가 {@code Critical} 이었다.
 *
 * <p>벤더 어휘를 그대로 쓰지 않는 이유는 응답 없음을 함께 표현해야 하기 때문이다 — 그것은 Redfish 가
 * 답한 상태가 아니라 답하지 못한 상황이라 {@code TaskState} 에 자리가 없다.</p>
 *
 * <p><b>상수가 종결 상태 · 사유 코드 · 사람이 읽을 문구를 자기 값으로 든다.</b> 소비처가 상태를 보고
 * 다시 분기해 그 셋을 고르면 상태가 늘 때마다 그 분기가 함께 자란다({@link FirmwareAxisReason} 과
 * 같은 관례).</p>
 */
@RequiredArgsConstructor
@Getter
public enum FlashTaskState {

    /** 아직 굽는 중이다({@code New} · {@code Running}) — 시한 안이면 기다린다. */
    RUNNING(null, FlashLedger.FLASH_TIMEOUT, "굽는 중"),

    /** 전송과 굽기가 끝났다({@code Completed}) — 그 축의 step 을 닫는다. */
    COMPLETED(ProvisioningStatus.SUCCEEDED, FlashLedger.FLASH_COMPLETED, "전송 완료"),

    /** BMC 가 실패로 종결했다({@code Exception}) — 그 축을 실패로 닫는다. */
    FAILED(ProvisioningStatus.FAILED, FlashLedger.FLASH_EXCEPTION, "BMC 가 굽기를 실패로 종결했습니다"),

    /**
     * Task 를 조회하지 못했다. BMC 가 굽기를 마친 직후 스스로 재기동하는 구간이 실측에서 5~10분이라,
     * 이것을 즉시 실패로 보면 <b>정상 완료를 실패로 뒤집는다</b>. 시한이 그 창을 덮는다.
     */
    UNREACHABLE(null, FlashLedger.BMC_UNREACHABLE, "BMC 에 닿지 못했습니다");

    /** 이 상태로 원장 행을 닫을 수 있으면 그 결과 — 아직 끝나지 않았으면 null. */
    private final ProvisioningStatus terminalStatus;

    /** 원장에 적을 사유 코드 — 시한 만료로 닫을 때도 이 값을 쓴다. */
    private final String reasonCode;

    /** 사람이 읽을 한 줄. */
    private final String userDetail;

    /** 지금 닫을 수 있는가 — 아니면 시한을 본다. */
    public boolean isTerminal() {
        return terminalStatus != null;
    }
}
