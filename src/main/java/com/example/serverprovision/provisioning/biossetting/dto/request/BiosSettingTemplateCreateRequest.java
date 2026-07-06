package com.example.serverprovision.provisioning.biossetting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * BIOS 세팅 템플릿 생성 요청. ({@code POST /provisioning/bios-setting})
 *
 * <p>{@code attributes} 는 기존 PoC 저장 계약과 동일한 wire — 편집기 JS 가 기본값 대비 변경분(diff)만
 * 폼 문자열로 실어 보내고, 타입 인식·검증·coerce 는 서버가 registry 로 수행한다(저장 경계에서 VO 화).
 * 보드는 {@code boardModelId}(management BoardModel FK — 사용자 지시로 varchar boardKey 에서 전환)로 지정한다.</p>
 */
public record BiosSettingTemplateCreateRequest(

        @NotBlank(message = "템플릿 명칭은 필수 입력값입니다.")
        @Size(max = 128, message = "템플릿 명칭은 128자 이하여야 합니다.")
        String name,

        @Size(max = 1024, message = "설명은 1024자 이하여야 합니다.")
        String description,

        @NotNull(message = "보드 모델 ID는 필수 값입니다.")
        Long boardModelId,

        @NotNull(message = "속성 변경분은 null일 수 없습니다.")
        Map<String, String> attributes
) {
}
