package com.example.serverprovision.management.subprogram.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기존 디렉토리 등록 Request. 업로드 없이 이미 콘텐츠가 차 있는 디렉토리를 Subprogram 자원으로 claim 한다.
 * Subprogram 은 Driver / Utility 두 kind 가 단일 엔티티에 통합 — kind 와 scope (board/common) 는 URL 에서 받는다.
 */
public record SubprogramRegisterExistingRequest(

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
        String description
) {
}
