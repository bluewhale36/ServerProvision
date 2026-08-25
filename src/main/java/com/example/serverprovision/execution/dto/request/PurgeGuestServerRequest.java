package com.example.serverprovision.execution.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 회수 서버 영구 삭제 확인(U6 D-5) — 확인 입력은 systemUUID 의 마지막 {@code -} 다음 세그먼트다.
 * 빈 값(형식 위반)은 Bean Validation 400, 값 불일치는 서비스 가드의 {@code TypedNameMismatchException} 400.
 */
public record PurgeGuestServerRequest(

        @NotBlank(message = "확인을 위해 systemUUID 의 마지막 '-' 다음 값을 입력해 주세요.")
        String typedSuffix
) {
}
