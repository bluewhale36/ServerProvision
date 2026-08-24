package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import org.springframework.stereotype.Component;

/**
 * 3행 — 착수 전인데 재료나 수단이 갖춰지지 않았다(E2-2 §5). 결손 사다리(E2-1-b)가 받는 자리다.
 *
 * <p>준비도 판정을 <b>착수 전 조건과 묶은</b> 것이 이 행의 요점이다. 착수한 뒤에도 준비도를 보면
 * 집행 도중 자원이 무너졌을 때 굽는 중인 Task 를 놓친다 — 진입 게이트가 착수한 게스트를 준비도보다
 * 먼저 거르는 것과 같은 판단이다.</p>
 *
 * <p>지원하는 provider 가 없는 경우도 여기서 걸린다(D-6) — BMC 하드웨어가 없어 Redfish 를 쓸 수 없는
 * 게스트는 굽지도 확인하지도 못하므로 <b>굽기 전에 막는다.</b> 굽고 나서 확인하지 못하는 상태가 최악이다.</p>
 */
@Component
public class SkipUnreadyStep implements FlashStep {

    @Override
    public int order() {
        return 3;
    }

    @Override
    public boolean matches(FlashContext context) {
        if (!context.beforeStart()) {
            return false;
        }
        return !context.supported() || context.resolution() == null
                || context.resolution().grade() == ReadinessGrade.BLOCKED;
    }

    @Override
    public void execute(FlashContext context) {
        // 진입하지 않는다 — 대기와 시한은 진입 게이트(E2-1-b)가 이미 맡고 있다.
    }
}
