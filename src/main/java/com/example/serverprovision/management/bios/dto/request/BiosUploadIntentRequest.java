package com.example.serverprovision.management.bios.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * BIOS 펌웨어 업로드 Intent 핸드셰이크 Request (XHR JSON).
 *
 * <p>파일 바이트를 올리기 전에 서버에서 하드 조건을 검증해 낭비를 방지한다 :
 * (1) 보드 활성 · (2) (board, version) 중복 여부 · (3) 경로 해석(디렉토리면 파일명 append) 결과와
 * 업로드 원본 파일명의 금지 파일명 검사 · (4) 대상 디렉토리 상태(비어있음 · 마커 충돌 없음 ·
 * 상위 디렉토리 존재 / allowCreateDirectory).</p>
 *
 * <p>R12-1 — intent 는 <b>업로드 경로 전용</b>이다. 업로드 파일 없이 기존 파일을 등록(claim)하는
 * 요청은 토큰 없이 등록 본체로 직행한다(ISO 선례와 동일).</p>
 */
public record BiosUploadIntentRequest(

		@NotBlank(message = "펌웨어 파일 경로를 입력해주세요.")
		@Size(max = 1024, message = "펌웨어 파일 경로는 1024자 이하로 입력해주세요.")
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
