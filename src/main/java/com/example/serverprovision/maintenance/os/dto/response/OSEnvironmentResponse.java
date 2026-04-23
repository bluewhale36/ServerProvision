package com.example.serverprovision.maintenance.os.dto.response;

import java.util.List;

/**
 * 설치 환경(installation environment) 응답 DTO.
 * Step 4 에서 Service 층의 정식 factory 와 연결될 예정.
 */
public record OSEnvironmentResponse(
        Long id,
        String environmentCode,
        String displayName,
        String description,
        boolean isDefault,
        List<String> groupCodes,
        List<IsoProvisionView> providers
) {}
