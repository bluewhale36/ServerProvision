package com.example.serverprovision.management.os.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * ISO 수정 요청.
 */
public record ISOUpdateRequest(
        @NotBlank(message = "ISO 경로를 입력하세요.")
        String isoPath,

        String description
) {}
