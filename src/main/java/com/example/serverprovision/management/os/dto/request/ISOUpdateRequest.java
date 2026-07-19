package com.example.serverprovision.management.os.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ISO 수정 요청.
 */
public record ISOUpdateRequest(
		@NotBlank(message = "ISO 경로를 입력하세요.")
		@Size(max = 1024, message = "ISO 경로는 1024자 이하로 입력해주세요.")
		String isoPath,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description
) {

}
