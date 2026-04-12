package com.example.serverprovision.domain.os.dto;

import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record OSMetadataDTO(
        @JsonProperty("id")              Long id,
        @JsonProperty("osName")          OSName osName,
        @JsonProperty("osVersion")       String osVersion,
        @JsonProperty("isoMountPath")    String isoMountPath,
        @JsonProperty("ksTemplatePath")  String ksTemplatePath,
        @JsonProperty("isEnabled")       boolean isEnabled,
        @JsonProperty("createdAt")       LocalDateTime createdAt,
        @JsonProperty("updatedAt")       LocalDateTime updatedAt
) {
    public static OSMetadataDTO from(OSMetadata osMetadata) {
        return OSMetadataDTO.builder()
                .id(osMetadata.getId())
                .osName(osMetadata.getOsName())
                .osVersion(osMetadata.getOsVersion())
                .isoMountPath(osMetadata.getIsoMountPath())
                .ksTemplatePath(osMetadata.getKsTemplatePath())
                .isEnabled(osMetadata.isEnabled())
                .createdAt(osMetadata.getCreatedAt())
                .updatedAt(osMetadata.getUpdatedAt())
                .build();
    }

    // OS 목록 화면의 그룹핑 뷰 — 동일 OSName 을 하나의 그룹으로 묶는다
    public record Group(
            OSName osName,
            List<OSMetadataDTO> items
    ) {}
}
