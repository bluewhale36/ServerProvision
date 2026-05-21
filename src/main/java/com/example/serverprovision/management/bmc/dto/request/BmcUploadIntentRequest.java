package com.example.serverprovision.management.bmc.dto.request;

import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import jakarta.validation.constraints.*;

public record BmcUploadIntentRequest(

		@NotBlank(message = "대상 디렉토리 경로를 입력해주세요.")
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
