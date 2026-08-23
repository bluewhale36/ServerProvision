package com.example.serverprovision.management.bmc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BMC 펌웨어 등록 Request.
 *
 * <p>R12-2 — 번들(폴더 · zip) 업로드 방식을 폐지하고 BIOS · ISO 와 동일한 단일 폼으로 통합했다.
 * {@code firmwarePath} 가 디렉토리를 가리키면 업로드 파일명을 이어 붙이고, 파일 경로면 그대로 쓴다.
 * 같은 요청에 업로드 파일이 있으면 그 경로에 저장하고, 없으면 그 경로의 기존 파일을 등록(claim)한다.</p>
 */
public record BmcCreateRequest(

		@NotBlank(message = "이름을 입력해주세요.")
		@Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
		String name,

		@NotBlank(message = "버전을 입력해주세요.")
		@Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
		String version,

		// HF4-2 — firmware_path(1024) 외에 legacy 컬럼 file_path VARCHAR(255) NOT NULL 에도 동일 값이
		// 미러링되므로(BoardBMC.legacyFilePath) effective DB 제약인 255 로 정렬. legacy 컬럼 제거 시 1024 복원.
		@NotBlank(message = "펌웨어 파일 경로를 입력해주세요.")
		@Size(max = 255, message = "펌웨어 파일 경로는 255자 이하로 입력해주세요.")
		String firmwarePath,

		@Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
		String description,

		/**
		 * 상위 디렉토리가 없을 때 자동 생성할지 여부. 기본 false.
		 */
		boolean allowCreateDirectory
) {

}
