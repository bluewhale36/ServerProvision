package com.example.serverprovision.maintenance.os.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * ISO 신규 등록 요청. 부모 OSImage 는 URL 경로 {@code /os/{osId}/iso} 로부터 결정된다.
 * {@code allowCreateDirectory} 가 true 면 isoPath 상위 디렉토리가 없을 때 자동 생성한다.
 */
public record ISOCreateRequest(
        @NotBlank(message = "ISO 경로를 입력하세요.")
        String isoPath,

        String description,

        boolean allowCreateDirectory
) {}
