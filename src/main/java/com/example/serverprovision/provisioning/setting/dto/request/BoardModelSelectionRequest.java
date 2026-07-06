package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 메인보드 모델 선택 — {@code {"mode":"AUTO"}} 또는 {@code {"mode":"SPECIFIED","boardModelId":1}}.
 *
 * <p>"자동 = null id" 같은 의미 있는 null 대신 선택 의도를 타입으로 명시한다(Primitive Obsession 금지).
 * AUTO 해석(실행 시점 보드 감지)은 execution 도메인의 몫이다.</p>
 */
public record BoardModelSelectionRequest(

        @NotNull(message = "보드 모델 선택 방식은 필수 값입니다.")
        BoardModelSelectionMode mode,

        Long boardModelId
) {

    @JsonCreator
    public BoardModelSelectionRequest(
            @JsonProperty("mode")         BoardModelSelectionMode mode,
            @JsonProperty("boardModelId") Long boardModelId
    ) {
        this.mode         = mode;
        this.boardModelId = boardModelId;
    }

    /** 방식과 id 의 정합 — SPECIFIED 는 id 필수, AUTO 는 id 없음(모호한 계약 차단). */
    @AssertTrue(message = "직접 지정은 보드 모델 ID가 필수이며, 자동 감지는 ID를 보낼 수 없습니다.")
    public boolean isModeConsistent() {
        if (mode == null) return true;  // mode 자체의 @NotNull 위반이 이미 보고된다 — 중복 오류 방지.
        return (mode == BoardModelSelectionMode.SPECIFIED) == (boardModelId != null);
    }

    public boolean isAuto() {
        return mode == BoardModelSelectionMode.AUTO;
    }
}
