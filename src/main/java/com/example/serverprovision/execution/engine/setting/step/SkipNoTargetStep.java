package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.engine.setting.SettingCursor;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 4행 — 감지 보드와 일치하는 BIOS 템플릿이 없다. 실패가 아니라 건너뜀이다 — 열린 행이 있으면(재개 중 할당이 바뀜)
 * 그 행을 닫는다. BIOS 목표가 없어도 BMC 표준은 밟아야 하므로 phase 종결이 아니라 다음 축으로 간다(E3-2 D-2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkipNoTargetStep implements SettingStep {

    private final SettingLedger ledger;
    private final SettingCursor settingCursor;

    @Override
    public int order() {
        return 4;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.axis() == SettingAxis.BIOS && context.target() != null && context.target().isEmpty();
    }

    @Override
    public void execute(SettingContext context) {
        String detail = "감지 보드와 일치하는 BIOS 세팅 템플릿이 없습니다";
        context.runningRow().ifPresentOrElse(
                row -> ledger.close(row, ProvisioningStatus.SKIPPED, SettingLedger.NO_TARGET, detail, context.now()),
                () -> ledger.instant(context.server(), SettingAxis.BIOS.getStep(), ProvisioningStatus.SKIPPED,
                        SettingLedger.NO_TARGET, detail, context.now()));
        settingCursor.afterAxis(SettingAxis.BIOS, context.progress(), context.server().getId(), context.now());
        log.info("[setting] {} — 적용할 BIOS 설정 없음, 건너뜀", context.server().getId());
    }
}
