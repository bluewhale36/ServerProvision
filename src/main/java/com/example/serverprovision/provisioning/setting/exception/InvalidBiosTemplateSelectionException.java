package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * BIOS 세팅 템플릿 선택 규칙 위반 (400, field-bound=biosSettingTemplateIds).
 * UI 는 보드 selector 연동 필터(SPECIFIED: 해당 보드 1개 / AUTO: 보드당 1개)로 1차 차단한다.
 */
public class InvalidBiosTemplateSelectionException extends FieldBoundBadRequestException {

    private InvalidBiosTemplateSelectionException(String message) {
        super(message, "biosSettingTemplateIds");
    }

    /** AUTO — 같은 보드의 템플릿 2개 이상(실행 시 어느 것을 적용할지 결정 불능). */
    public static InvalidBiosTemplateSelectionException duplicatedBoard(String boardModelName) {
        return new InvalidBiosTemplateSelectionException(
                "같은 메인보드(" + boardModelName + ")의 템플릿은 1개만 선택할 수 있습니다.");
    }

    /** SPECIFIED — 지정 보드와 다른 보드의 템플릿. */
    public static InvalidBiosTemplateSelectionException boardMismatch(Long templateId) {
        return new InvalidBiosTemplateSelectionException(
                "지정한 메인보드의 템플릿이 아닙니다: #" + templateId);
    }
}
