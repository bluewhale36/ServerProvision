package com.example.serverprovision.management.board.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 메인보드 모델 수정 요청. vendor 는 변경 불가 — 바꾸려면 삭제 후 재등록한다.
 */
public record BoardModelUpdateRequest(
        @NotBlank(message = "모델명을 입력하세요.")
        String modelName,

        String description
) {}
