package com.example.serverprovision.maintenance.os.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * OS 이미지 수정 요청. OSName 은 변경 불가 (plan §7 A1) — 변경 필요 시 soft 삭제 후 재등록.
 */
public record OSImageUpdateRequest(
        @NotBlank(message = "OS 버전을 입력하세요.")
        String osVersion,

        String description
) {}
