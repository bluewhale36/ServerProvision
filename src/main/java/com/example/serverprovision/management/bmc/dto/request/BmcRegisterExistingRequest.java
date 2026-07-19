package com.example.serverprovision.management.bmc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기존 디렉토리 등록 Request. 업로드 없이 이미 콘텐츠가 차 있는 디렉토리를 BMC 펌웨어 자원으로 claim 한다.
 */
public record BmcRegisterExistingRequest(

		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
		String name,

		@NotBlank(message = "버전을 입력해주세요.")
		@Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
		String version,

		// HF4-2 — legacy 컬럼 file_path VARCHAR(255) NOT NULL 미러링 때문에 255 로 정렬 (BmcCreateRequest 동일).
		@NotBlank(message = "기존 디렉토리 경로를 입력해주세요.")
		@Size(max = 255, message = "경로는 255자 이하로 입력해주세요.")
		String targetDirectory,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description,

		@Size(max = 512, message = "진입점 경로는 512자 이하로 입력해주세요.")
		String entrypointRelativePath
) {

}
