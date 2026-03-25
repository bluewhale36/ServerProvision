package com.example.serverprovision.domain.os.dto;

import com.example.serverprovision.domain.os.entity.OSEnvironment;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record OSEnvironmentDTO(
        Long id,
        OSMetadataDTO metadata,
        String environmentCode,
        String displayName,
        String description,
        boolean isDefault
) {
    public static OSEnvironmentDTO from(OSEnvironment osEnvironment) {
        return OSEnvironmentDTO.builder()
                .id(osEnvironment.getId())
                .metadata(OSMetadataDTO.from(osEnvironment.getOsMetadata()))
                .environmentCode(osEnvironment.getEnvironmentCode())
                .displayName(osEnvironment.getDisplayName())
                .description(osEnvironment.getDescription())
                .isDefault(osEnvironment.isDefault())
                .build();
    }

    public String getInstallationScript() {
        return "@^" + environmentCode;
    }
}
