package com.example.serverprovision.management.os.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ISO 신규 등록 요청. 부모 OSMetadata 는 URL 경로 {@code /os/{osId}/iso} 로부터 결정된다.
 * {@code allowCreateDirectory} 가 true 면 isoPath 상위 디렉토리가 없을 때 자동 생성한다.
 */
public record ISOCreateRequest(
		@NotBlank(message = "ISO 경로를 입력하세요.")
		@Size(max = 1024, message = "ISO 경로는 1024자 이하로 입력해주세요.")
		String isoPath,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description,

		boolean allowCreateDirectory
) {

}
