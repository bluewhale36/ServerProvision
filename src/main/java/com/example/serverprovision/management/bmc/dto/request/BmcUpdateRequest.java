package com.example.serverprovision.management.bmc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BMC 메타데이터 수정 Request.
 */
public record BmcUpdateRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "버전을 입력해주세요.")
        @Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
        String version,

        @Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
        String description
) {
}
