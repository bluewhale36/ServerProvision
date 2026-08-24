package com.example.serverprovision.execution.engine.firmware.step;

import org.springframework.stereotype.Component;

/**
 * 1행 — 실행 창 밖(E2-2 §5). 회수 · 미개시 · 실패 · 종단은 집행 대상이 아니다.
 *
 * <p>가장 위에 두는 이유는 아래 행들이 전부 "이 게스트가 지금 프로비저닝 중" 임을 전제하기 때문이다.
 * 실패한 게스트를 계속 집으면 자동 재시도가 되어 버리는데, 펌웨어 실패에는 자동 재시도가 없다.</p>
 */
@Component
public class SkipOutOfWindowStep implements FlashStep {

    @Override
    public int order() {
        return 1;
    }

    @Override
    public boolean matches(FlashContext context) {
        return !context.inExecutionWindow();
    }

    @Override
    public void execute(FlashContext context) {
        // 아무것도 하지 않는다 — 이 행의 존재 자체가 "건너뛴다" 는 판정이다.
    }
}
