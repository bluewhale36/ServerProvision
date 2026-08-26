package com.example.serverprovision.execution.engine.setting;

/**
 * 이 게스트에 적용할 BMC 표준 세팅 재료(E3-2 D-1) — 표준값(시스템 설정) + 감지 보드의 Fan Profile(없을 수 있다).
 * BIOS 목표({@link BiosSettingTarget})와 달리 정의서에서 오지 않는다 — 표준이라 게스트마다 같다.
 */
public record BmcSettingTarget(BmcStandardSettings standard, String boardModelName,
                               FanProfileResources.FanProfile fanProfile) {

    public boolean hasFanProfile() {
        return fanProfile != null;
    }
}
