package com.example.serverprovision.management.subprogram.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Subprogram 등록 Request.
 * <p>등록 시점에는 {@code entrypointRelativePath} 를 입력받지 않는다 (MA5-D5). 등록 후 편집 화면에서 설정.</p>
 */
public record SubprogramCreateRequest(

		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
		String name,

		@NotBlank(message = "버전을 입력해주세요.")
		@Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
		String version,

		@NotBlank(message = "대상 디렉토리 경로를 입력해주세요.")
		@Size(max = 1024, message = "대상 디렉토리 경로는 1024자 이하로 입력해주세요.")
		String targetDirectory,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description,

		boolean allowCreateDirectory
) {

}
