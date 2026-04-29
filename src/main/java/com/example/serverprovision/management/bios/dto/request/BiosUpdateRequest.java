package com.example.serverprovision.management.bios.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * BIOS 메타데이터 수정 Request.
 * <p>번들 내용(treeRootPath / entrypointRelativePath / manifestHash 등) 은 여기서 수정하지 않는다 —
 * 파일 교체는 soft delete 후 번들 재업로드 흐름을 통해서만 가능. 메타데이터(name / version / description) 만 갱신.</p>
 */
public record BiosUpdateRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 128, message = "이름은 128자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "버전을 입력해주세요.")
        @Size(max = 64, message = "버전은 64자 이하로 입력해주세요.")
        String version,

        @Size(max = 1024, message = "설명은 1024자 이하로 입력해주세요.")
        String description
) {
}
