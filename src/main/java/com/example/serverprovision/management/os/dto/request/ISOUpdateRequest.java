package com.example.serverprovision.management.os.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ISO 수정 요청.
 */
public record ISOUpdateRequest(
		@NotBlank(message = "ISO 경로를 입력해야 합니다.")
		@Size(max = 1024, message = "ISO 경로는 1024자 이하로 입력해야 합니다.")
		String isoPath,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해야 합니다.")
		String description
) {

}
