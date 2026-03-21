package com.example.serverprovision.domain.os.dto;

import lombok.Builder;

@Builder
public record OSMetadataCreateDTO(
        String osName,
        String osVersion,
        String isoMountPath,
        String ksTemplatePath,
        boolean isEnabled
) {
}
