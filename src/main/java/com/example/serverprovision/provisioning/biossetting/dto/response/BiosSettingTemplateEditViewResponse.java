package com.example.serverprovision.provisioning.biossetting.dto.response;

import com.example.serverprovision.provisioning.dto.response.BiosSetupPageResponse;

/**
 * 수정 편집기 뷰모델 — 생성과 동일한 편집기 화면에 템플릿 메타(pre-fill)와
 * 저장값 overlay 가 주입된 트리 뷰모델을 함께 실어 나른다.
 */
public record BiosSettingTemplateEditViewResponse(
        Long id,
        String name,
        String description,
        Long boardModelId,
        String boardModelName,
        BiosSetupPageResponse bios
) {
}
