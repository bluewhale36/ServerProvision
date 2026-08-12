package com.example.serverprovision.provisioning.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 그룹 이름 변경 요청 (U3-4). 제약은 생성과 같다 — 같은 컬럼에 들어가는 같은 값이다. */
public record RenameGroupRequest(
        @NotBlank(message = "그룹 이름을 입력하세요.")
        @Size(max = 128, message = "그룹 이름은 128자 이하로 입력해주세요.")
        String name
) {
}
