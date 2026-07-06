package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;

/**
 * BASIC_SETTING 단계 폼의 BIOS 세팅 템플릿 선택지 — 보드 정보를 함께 실어 UI 가
 * 보드 selector 연동 필터(SPECIFIED 1개 / AUTO 보드당 1개)를 적용한다.
 */
public record BiosTemplateOptionResponse(
        Long id,
        String name,
        Long boardModelId,
        String boardModelName
) {
    public static BiosTemplateOptionResponse from(BiosSettingTemplate template) {
        return new BiosTemplateOptionResponse(
                template.getId(), template.getName(),
                template.getBoardModel().getId(), template.getBoardModel().getModelName());
    }
}
