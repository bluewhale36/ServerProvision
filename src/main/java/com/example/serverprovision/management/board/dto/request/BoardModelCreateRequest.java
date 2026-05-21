package com.example.serverprovision.management.board.dto.request;

import com.example.serverprovision.management.board.enums.Vendor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 신규 메인보드 모델 등록 요청. (vendor, modelName) 조합은 활성 레코드 안에서 유일해야 한다.
 * 일부 제조사는 서버 모델명을 기준으로 기재할 수 있으므로 {@code modelName} 은 자유 문자열로 둔다.
 */
public record BoardModelCreateRequest(
		@NotNull(message = "제조사를 선택하세요.")
		Vendor vendor,

		@NotBlank(message = "모델명을 입력하세요.")
		String modelName,

		String description
) {

}
