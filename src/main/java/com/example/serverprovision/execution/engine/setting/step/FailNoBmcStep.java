package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.setting.SettingLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 4행 — 목표는 있는데 BMC 가 없다(미검출 또는 지원 흐름 없음). E2-2 는 이 경우 보류하지만 설정 적용은
 * BMC 없이 불가한 것이 확정 사실이고 보류하면 시한 없이 멈춘다 — 운영자가 알아야 할 실패다(D-12).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailNoBmcStep implements SettingStep {

    private final SettingLedger ledger;

    @Override
    public int order() {
        return 4;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.target() != null && !context.target().isEmpty() && !context.bmcDetected();
    }

    @Override
    public void execute(SettingContext context) {
        ledger.failAtCursor(context.server(), context.progress(), SettingLedger.BMC_REQUIRED,
                "BIOS 설정 적용에는 BMC 가 필요한데 진단 수집이 BMC 를 찾지 못했습니다", context.now());
        log.warn("[setting] {} — BMC 없이 BIOS 설정을 적용할 수 없어 실패 전환", context.server().getId());
    }
}
