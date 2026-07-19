package com.example.serverprovision.management.bmc.dto.request;

import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import jakarta.validation.constraints.*;

public record BmcUploadIntentRequest(

		// HF4-2 — legacy 컬럼 file_path VARCHAR(255) NOT NULL 미러링 때문에 255 로 정렬 (BmcCreateRequest 동일).
		@NotBlank(message = "대상 디렉토리 경로를 입력해주세요.")
		@Size(max = 255, message = "대상 디렉토리 경로는 255자 이하로 입력해주세요.")
		String targetDirectory,

		@NotNull(message = "업로드 방식을 지정해야 합니다.")
		BmcUploadMode uploadMode,

		@Positive(message = "파일 수는 1 이상이어야 합니다.")
		int fileCount,

		@PositiveOrZero(message = "총 바이트는 0 이상이어야 합니다.")
		long totalBytes,

		@NotBlank(message = "버전은 필수입니다.")
		@Size(max = 64)
		String version,

		boolean allowCreateDirectory,

		@Size(max = 512)
		String entrypointRelativePath
) {

}
