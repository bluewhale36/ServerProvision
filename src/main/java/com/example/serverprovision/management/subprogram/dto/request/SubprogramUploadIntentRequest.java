package com.example.serverprovision.management.subprogram.dto.request;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.validation.RegistrableOsName;

import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import jakarta.validation.constraints.*;

/**
 * Subprogram 업로드 직전 의도 검증 Request.
 * <p>BIOS / BMC 패턴과 동일하게 토큰 발급 전 사전 검증 (대상 디렉토리 점유 / 활성 중복 등) 후
 * uploadToken 을 반환받아 본 업로드에 동봉한다.</p>
 */
public record SubprogramUploadIntentRequest(

		// HF30-2 — 등록 본체와 같은 키 (종류, 범위, 이름, 버전)로 업로드 전에 판정하려고 받는다. 제약은 SubprogramCreateRequest 와 같다.
		@NotBlank(message = "이름을 입력해야 합니다.")
		@Size(max = 128, message = "이름은 128자 이하로 입력해야 합니다.")
		String name,

		@NotBlank(message = "대상 디렉토리 경로를 입력해야 합니다.")
		@Size(max = 1024, message = "대상 디렉토리 경로는 1024자 이하로 입력해야 합니다.")
		String targetDirectory,

		@NotNull(message = "업로드 방식을 지정해야 합니다.")
		SubprogramUploadMode uploadMode,

		@Positive(message = "파일 수는 1 이상이어야 합니다.")
		int fileCount,

		@PositiveOrZero(message = "총 바이트는 0 이상이어야 합니다.")
		long totalBytes,

		@NotBlank(message = "버전을 입력해야 합니다.")
		@Size(max = 64)
		String version,

		boolean allowCreateDirectory,

		@RegistrableOsName   // S23-1 — 미지원 OS 상수는 새로 고를 수 없다
		OSName osName
) {
}
