package com.example.serverprovision.domain.os.dto;

import com.example.serverprovision.domain.os.entity.OSEnvironment;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record OSEnvironmentDTO(
        @JsonProperty("id")              Long id,
        @JsonProperty("metadata")        OSMetadataDTO metadata,
        @JsonProperty("environmentCode") String environmentCode,
        @JsonProperty("displayName")     String displayName,
        @JsonProperty("description")     String description,
        @JsonProperty("isDefault")       boolean isDefault
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
