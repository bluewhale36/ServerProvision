package com.example.serverprovision.management.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 메인보드 모델 수정 요청. vendor 는 변경 불가 — 바꾸려면 삭제 후 재등록한다.
 */
public record BoardModelUpdateRequest(
		@NotBlank(message = "모델명을 입력하세요.")
		@Size(max = 128, message = "모델명은 128자 이하로 입력해주세요.")
		String modelName,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description
) {

}
