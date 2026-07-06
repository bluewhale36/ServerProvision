package com.example.serverprovision.provisioning.biossetting.dto.response;

import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;

import java.time.LocalDateTime;

/**
 * 템플릿 목록 행 + 생성 응답. 변경 속성 수는 values 에서 파생한다(denorm 컬럼 없음 — 설계 확정).
 */
public record BiosSettingTemplateSummaryResponse(
        Long id,
        String name,
        String description,
        Long boardModelId,
        String boardModelName,
        int attributeCount,
        boolean inUse,
        LocalDateTime updatedAt
) {

    public static BiosSettingTemplateSummaryResponse from(BiosSettingTemplate template, boolean inUse) {
        return new BiosSettingTemplateSummaryResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getBoardModel().getId(),
                template.getBoardModel().getModelName(),
                template.getValues().size(),
                inUse,
                template.getUpdatedAt());
    }
}
