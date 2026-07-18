package com.example.serverprovision.execution.dto.request;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import jakarta.validation.constraints.NotNull;

/** step 시작 보고(E1-0b) — RUNNING 원장 행을 연다. */
public record StepOpenRequest(

        @NotNull(message = "stepCode 는 필수입니다.")
        ProvisioningPhaseStep stepCode
) {
}
