package com.example.serverprovision.management.bios.dto.request;

import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import jakarta.validation.constraints.*;

/**
 * BIOS 번들 업로드 Intent 핸드셰이크 Request (XHR JSON).
 * <p>파일 바이트를 한 건도 올리기 전에 서버에서 하드 조건을 검증해 낭비를 방지한다 :
 * (1) 보드 활성 · (2) (board, version) 중복 여부 · (3) targetDirectory 비어있음 또는 soft-deleted 점유 ·
 * (4) marker 충돌 없음 · (5) 상위 디렉토리 존재 / allowCreateDirectory.</p>
 */
public record BiosUploadIntentRequest(

		@NotBlank(message = "대상 디렉토리 경로를 입력해주세요.")
		@Size(max = 1024, message = "대상 디렉토리 경로는 1024자 이하로 입력해주세요.")
		String targetDirectory,

		@NotNull(message = "업로드 방식을 지정해야 합니다.")
		BiosUploadMode uploadMode,

		@Positive(message = "파일 수는 1 이상이어야 합니다.")
		int fileCount,

		@PositiveOrZero(message = "총 바이트는 0 이상이어야 합니다.")
		long totalBytes,

		@NotBlank(message = "버전은 필수입니다.")
		@Size(max = 64)
		String version,

		boolean allowCreateDirectory,

		/**
		 * 진입점 override. Intent 단계에서는 서버 디렉토리가 아직 비어 있으므로 파일 실재 검증은 불가하지만,
		 * 빈 값이 아니면 "자동 탐지에 맡기지 않음" 의사 표시로 저장소에 기록해 둔다.
		 */
		@Size(max = 512)
		String entrypointRelativePath
) {

}
