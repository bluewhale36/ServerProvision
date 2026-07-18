package com.example.serverprovision.execution.dto.request;

import com.example.serverprovision.execution.enums.ProvisioningStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** step 종료 보고(E1-0b) — 대상 행 식별자(stepId, 경로변수)에 바인딩된 닫힘. */
public record StepCloseRequest(

        @NotNull(message = "status 는 필수입니다.")
        ProvisioningStatus status,

        /** 구조화 결과(JSON 문자열) — 수집 payload(E1-2) · skip 사유 등. 없으면 null. */
        String statusMeta
) {

    /** 종료 보고는 종결 상태만 유효 — RUNNING/PENDING 으로 닫는 요청은 계약 위반(400). */
    @AssertTrue(message = "종료 보고 상태는 SUCCEEDED / FAILED / SKIPPED 만 허용됩니다.")
    public boolean isTerminalStatus() {
        return status == null      // @NotNull 위반이 이미 보고된다 — 중복 오류 방지
                || status == ProvisioningStatus.SUCCEEDED
                || status == ProvisioningStatus.FAILED
                || status == ProvisioningStatus.SKIPPED;
    }
}
