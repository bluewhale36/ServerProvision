package com.example.serverprovision.domain.os.dto;

import com.example.serverprovision.domain.os.model.enums.OSName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record OSMetadataUpdateDTO(
        Long targetId,

        @NotNull(message = "OS 이름은 필수 값입니다.")
        OSName osName,

        @NotBlank(message = "OS 버전은 필수 입력값입니다.")
        String osVersion,

        @NotBlank(message = "ISO 마운트 경로는 필수 입력값입니다.")
        String isoMountPath,

        String ksTemplatePath,

        Boolean isEnabled
) {
}
