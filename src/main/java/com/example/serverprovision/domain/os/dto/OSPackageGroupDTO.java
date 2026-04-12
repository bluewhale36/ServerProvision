package com.example.serverprovision.domain.os.dto;


import com.example.serverprovision.domain.os.entity.OSPackageGroup;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record OSPackageGroupDTO(
        @JsonProperty("id")            Long id,
        @JsonProperty("osEnvironment") OSEnvironmentDTO osEnvironment,
        @JsonProperty("groupCode")     String groupCode,
        @JsonProperty("displayName")   String displayName,
        @JsonProperty("description")   String description,
        @JsonProperty("isDefault")     boolean isDefault
) {
    public static OSPackageGroupDTO from(OSPackageGroup osPackageGroup) {
        return OSPackageGroupDTO.builder()
                .id(osPackageGroup.getId())
                .osEnvironment(OSEnvironmentDTO.from(osPackageGroup.getOsEnvironment()))
                .groupCode(osPackageGroup.getGroupCode())
                .displayName(osPackageGroup.getDisplayName())
                .description(osPackageGroup.getDescription())
                .isDefault(osPackageGroup.isDefault())
                .build();
    }

    public String getInstallationScript() {
        return "@" + groupCode;
    }
}
