package com.example.serverprovision.management.bmc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BMC 펌웨어 등록 Request.
 */
public record BmcCreateRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "버전을 입력해주세요.")
        @Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
        String version,

        @NotBlank(message = "대상 디렉토리 경로를 입력해주세요.")
        @Size(max = 1024, message = "대상 디렉토리 경로는 1024자 이하로 입력해주세요.")
        String targetDirectory,

        @Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
        String description,

        boolean allowCreateDirectory,

        @Size(max = 512, message = "진입점 경로는 512자 이하로 입력해주세요.")
        String entrypointRelativePath
) {
}
