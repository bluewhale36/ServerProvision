package com.example.serverprovision.management.os.dto.request;

import com.example.serverprovision.management.os.enums.OSName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 신규 OS 이미지 등록 요청. OS 이미지는 이름과 버전만 가진다 —
 * 실제 파일 경로(ISO, kickstart 템플릿 등) 는 별도 하위 리소스에서 다룬다.
 * (osName, osVersion) 조합은 활성 레코드 안에서 유일해야 한다.
 */
public record OSImageCreateRequest(
        @NotNull(message = "OS 이름을 선택하세요.")
        OSName osName,

        @NotBlank(message = "OS 버전을 입력하세요.")
        String osVersion,

        String description
) {}
