package com.example.serverprovision.management.bios.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기존 디렉토리 등록 Request. 업로드 없이 이미 콘텐츠가 차 있는 디렉토리를 BIOS 번들 자원으로 claim 한다.
 * <p>업로드 모드의 {@link BiosCreateRequest} 와 달리 {@code uploadMode} / {@code allowCreateDirectory}
 * 가 없다 — 디렉토리는 이미 존재해야 하며, 바이트 전송이 발생하지 않는다.</p>
 */
public record BiosRegisterExistingRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "버전을 입력해주세요.")
        @Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
        String version,

        @NotBlank(message = "기존 디렉토리 경로를 입력해주세요.")
        @Size(max = 1024, message = "경로는 1024자 이하로 입력해주세요.")
        String targetDirectory,

        @Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
        String description,

        /**
         * 진입점 상대경로 override. 빈 값이면 트리에서 자동 탐지.
         */
        @Size(max = 512, message = "진입점 경로는 512자 이하로 입력해주세요.")
        String entrypointRelativePath
) {
}
