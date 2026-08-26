package com.example.serverprovision.execution.engine.setting.step;

import org.springframework.stereotype.Component;

/**
 * 3행 — 활성 할당이 없거나 정의서에 BASIC_SETTING 이 없다(창 밖). 판정 대상이 아니므로 아무것도 하지 않는다.
 * 이름에 phase 를 넣은 이유: E2-2 의 {@code firmware.step.SkipOutOfWindowStep} 과 단순명이 같으면 Spring 기본
 * 빈 이름이 충돌해 기동이 죽는다(CP5 F-1 — 단위 테스트는 컨텍스트를 올리지 않아 못 잡았다).
 */
@Component
public class SkipOutOfSettingWindowStep implements SettingStep {

    @Override
    public int order() {
        return 3;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.target() == null;
    }

    @Override
    public void execute(SettingContext context) {
        // 할 일 없음 — 커서는 다른 경로(할당 · 개시)가 움직인다.
    }
}
