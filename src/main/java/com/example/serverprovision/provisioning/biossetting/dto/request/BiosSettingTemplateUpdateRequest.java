package com.example.serverprovision.provisioning.biossetting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * BIOS 세팅 템플릿 수정 요청. ({@code PUT /provisioning/bios-setting/{id}})
 *
 * <p>{@code boardKey} 는 계약에 없다 — 생성 후 불변(템플릿의 유효 도메인). {@code attributes} 는
 * 전체 교체 의미론: 편집기가 기준선(registry 기본값) 대비 전체 변경분을 재수집해 보낸다.</p>
 */
public record BiosSettingTemplateUpdateRequest(

        @NotBlank(message = "템플릿 명칭은 필수 입력값입니다.")
        @Size(max = 128, message = "템플릿 명칭은 128자 이하여야 합니다.")
        String name,

        @Size(max = 1024, message = "설명은 1024자 이하여야 합니다.")
        String description,

        @NotNull(message = "속성 변경분은 null일 수 없습니다.")
        Map<String, String> attributes
) {
}
