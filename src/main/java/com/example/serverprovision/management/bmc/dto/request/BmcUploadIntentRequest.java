package com.example.serverprovision.management.bmc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * BMC 펌웨어 업로드 Intent 핸드셰이크 Request (XHR JSON).
 *
 * <p>R12-2 — intent 는 <b>업로드 경로 전용</b>이다. 업로드 파일 없이 기존 파일을 등록(claim)하는
 * 요청은 토큰 없이 등록 본체로 직행한다(BIOS · ISO 선례).</p>
 */
public record BmcUploadIntentRequest(

		// HF4-2 — legacy 컬럼 file_path VARCHAR(255) NOT NULL 미러링 때문에 255 로 정렬 (BmcCreateRequest 동일).
		@NotBlank(message = "펌웨어 파일 경로를 입력해주세요.")
		@Size(max = 255, message = "펌웨어 파일 경로는 255자 이하로 입력해주세요.")
		String firmwarePath,

		@NotBlank(message = "업로드 파일명이 누락되어 있습니다.")
		@Size(max = 512, message = "파일명은 512자 이하여야 합니다.")
		String fileName,

		@PositiveOrZero(message = "파일 크기는 0 이상이어야 합니다.")
		long fileSize,

		@NotBlank(message = "버전은 필수입니다.")
		@Size(max = 64)
		String version,

		boolean allowCreateDirectory
) {

}
