package com.example.serverprovision.maintenance.os.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * ISO 업로드 intent 핸드셰이크 요청 — 실제 바이트 전송 전에 서버에 사전 검증을 의뢰한다.
 * 서버는 (isoPath 중복, 상위 디렉토리 존재 여부 등) 판정 후 토큰을 발급하거나 409 로 거절한다.
 * {@code allowCreateDirectory} 가 true 면 디렉토리가 없어도 intent 를 통과시킨다.
 */
public record IsoUploadIntentRequest(
        @NotBlank String isoPath,
        @NotBlank String filename,
        @NotNull @Min(0) Long size,
        boolean allowCreateDirectory
) {}
