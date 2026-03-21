package com.example.serverprovision.domain.os.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record OSMetadataUpdateDTO(
        Long targetId,

        @NotBlank(message = "OS 이름은 필수 입력값입니다.")
        String osName,

        @NotBlank(message = "OS 버전은 필수 입력값입니다.")
        String osVersion,

        @NotBlank(message = "ISO 마운트 경로는 필수 입력값입니다.")
        String isoMountPath,

        @NotBlank(message = "Kickstart 템플릿 경로는 필수 입력값입니다.")
        String ksTemplatePath,

        boolean isEnabled
) {
}
