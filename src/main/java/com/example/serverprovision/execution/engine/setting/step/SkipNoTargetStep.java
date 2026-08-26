package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 3행 — 감지 보드와 일치하는 템플릿이 없다. 실패가 아니라 건너뜀이다 — BMC 유무와 무관하게 할 일이 없고,
 * D-12(BMC 결손 = 실패)는 목표가 있을 때의 판정이다. 열린 행이 있으면(재개 중 할당이 바뀜) 그 행을 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkipNoTargetStep implements SettingStep {

    private final SettingLedger ledger;
    private final PhaseCursorAdvancer cursorAdvancer;

    @Override
    public int order() {
        return 3;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.target() != null && context.target().isEmpty();
    }

    @Override
    public void execute(SettingContext context) {
        String detail = "감지 보드와 일치하는 BIOS 세팅 템플릿이 없습니다";
        context.runningRow().ifPresentOrElse(
                row -> ledger.close(row, ProvisioningStatus.SKIPPED, SettingLedger.NO_TARGET, detail, context.now()),
                () -> ledger.instant(context.server(), ProvisioningStatus.SKIPPED, SettingLedger.NO_TARGET, detail,
                        context.now()));
        cursorAdvancer.advanceOrComplete(context.progress(), context.server().getId(), context.now());
        log.info("[setting] {} — 적용할 BIOS 설정 없음, 건너뜀", context.server().getId());
    }
}
