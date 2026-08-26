package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 축 종결 뒤 커서(E3-2 D-2) — 다음 축이 있으면 같은 phase 안에서 그 step 으로 옮기고, 없으면 phase 완주로
 * {@link PhaseCursorAdvancer} 에 넘긴다. BIOS 목표가 없어(NO_TARGET) 건너뛴 게스트도 BMC 표준은 밟아야 하므로
 * "축 하나가 끝났다" 의 뒷일을 행마다 복붙하지 않고 여기 한곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class SettingCursor {

    private final PhaseCursorAdvancer phaseCursorAdvancer;

    public void afterAxis(SettingAxis axis, ProvisioningProgress progress, UUID guestId, LocalDateTime now) {
        axis.next().ifPresentOrElse(
                next -> progress.positionAt(next.getStep(), now),
                () -> phaseCursorAdvancer.advanceOrComplete(progress, guestId, now));
    }
}
