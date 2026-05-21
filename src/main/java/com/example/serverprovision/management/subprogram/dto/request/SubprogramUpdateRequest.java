package com.example.serverprovision.management.subprogram.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Subprogram 메타 + 진입점 수정 Request.
 * <p>{@code entrypointRelativePath} 는 등록 시점에 받지 않고 본 편집 화면에서 비로소 설정한다 (MA5-D5).
 * 비워두는 것 (null/blank) 도 허용 — 진입점 개념이 약한 자원 (드라이버 등) 은 그대로 둘 수 있다.</p>
 */
public record SubprogramUpdateRequest(
		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
		String name,

		@NotBlank(message = "버전을 입력해주세요.")
		@Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
		String version,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description,

		@Size(max = 512, message = "진입점 경로는 512자 이하로 입력해주세요.")
		String entrypointRelativePath
) {

}
