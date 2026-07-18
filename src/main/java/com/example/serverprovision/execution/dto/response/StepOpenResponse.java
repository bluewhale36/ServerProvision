package com.example.serverprovision.execution.dto.response;

import java.util.UUID;

/** step 시작 보고 응답 — 이후 종료 보고가 바인딩할 원장 행 식별자(멱등의 축, DEC-3). */
public record StepOpenResponse(UUID stepId) {
}
