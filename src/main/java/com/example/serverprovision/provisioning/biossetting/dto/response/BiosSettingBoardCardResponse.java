package com.example.serverprovision.provisioning.biossetting.dto.response;

/**
 * 템플릿 작성 랜딩의 보드 카드 — management 의 BoardModel 실데이터 기반(FK 전환).
 *
 * <p>{@code catalogAvailable} 은 해당 보드의 BIOS 카탈로그(registry/SetupData 파일) 등록 여부 —
 * false 면 카드 disabled + tooltip(UI 1차 차단), direct 진입은 로더의
 * {@code BiosBoardNotFoundException} 404 안전망이 받는다(같은 카탈로그 존재 판정).</p>
 */
public record BiosSettingBoardCardResponse(
        Long boardModelId,
        String modelName,
        String vendor,
        boolean catalogAvailable
) {
}
