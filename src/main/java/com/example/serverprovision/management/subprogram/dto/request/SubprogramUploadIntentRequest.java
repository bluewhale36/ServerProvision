package com.example.serverprovision.management.subprogram.dto.request;

import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Subprogram 업로드 직전 의도 검증 Request.
 * <p>BIOS / BMC 패턴과 동일하게 토큰 발급 전 사전 검증 (대상 디렉토리 점유 / 활성 중복 등) 후
 * uploadToken 을 반환받아 본 업로드에 동봉한다.</p>
 */
public record SubprogramUploadIntentRequest(

        @NotBlank(message = "대상 디렉토리 경로를 입력해주세요.")
        String targetDirectory,

        @NotNull(message = "업로드 방식을 지정해야 합니다.")
        SubprogramUploadMode uploadMode,

        @Positive(message = "파일 수는 1 이상이어야 합니다.")
        int fileCount,

        @PositiveOrZero(message = "총 바이트는 0 이상이어야 합니다.")
        long totalBytes,

        @NotBlank(message = "버전은 필수입니다.")
        @Size(max = 64)
        String version,

        boolean allowCreateDirectory
) {
}
