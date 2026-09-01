package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.firmware.AxisResolution;
import com.example.serverprovision.execution.engine.firmware.BmcIdentityGuard;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.service.FirmwareImageTokenRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * 5행 — 아직 손대지 않은 축을 굽는다(E2-2 §5). 축은 <b>한 번에 하나씩</b> 굽는다.
 *
 * <p>판정이 굽지 않기로 한 축은 그 사실만 원장에 남기고 지나간다 — 그 축을 포기해도 나머지는 진행할 수
 * 있기 때문이다. 이미 목표 버전인 축도 굽지 않는다(D-7): 굽는 행위 자체가 위험이므로 불필요한 flash 는
 * 피한다. 여기서 쓰는 버전 비교는 <b>등가 비교이지 순서 비교가 아니다</b> — 문자열로 순서를 정할 수
 * 없다는 결론(E2-1-a)은 "같은가" 를 보는 데는 적용되지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashAxisStep implements FlashStep {

    private final BmcIdentityGuard identityGuard;
    private final FirmwareImageTokenRegistry tokenRegistry;
    private final FlashLedger ledger;

    @Override
    public int order() {
        return 5;
    }

    @Override
    public boolean matches(FlashContext context) {
        return context.nextUntouchedAxis().isPresent();
    }

    @Override
    public void execute(FlashContext context) {
        FirmwareAxis axis = context.nextUntouchedAxis().orElseThrow();

        AxisResolution decided = context.resolutionOf(axis);
        if (decided == null || !decided.isSelected()) {
            context.progress().positionAt(axis.getStep(), context.now());
            String why = decided == null ? "구울 펌웨어가 정해지지 않았습니다" : decided.message(axis.label());
            ledger.skipAxis(context.server(), axis, why, context.now());
            return;
        }
        // 신원 확인이 커서 이동보다 먼저다(E2-4 CP5 F-2) — positionAt 이 lastTransitionAt 을 갱신하는데,
        // 신원 대기의 시한이 바로 그 시각을 기점으로 삼아 매 주기 자기 시한을 되감는 livelock 이 있었다.
        if (!identityGuard.confirm(context, axis)) {
            return;   // 커서 · 전이 시각 불변 — 시한이 흘러 만료되면 가드가 실패로 전환한다.
        }
        context.progress().positionAt(axis.getStep(), context.now());
        // 버전이 같아도 굽는다(2026-08-25 사용자 결정) — 커스텀 이미지(로고 변경본 등)는 버전이 같아도
        // 내용이 다르므로, 버전 비교 스킵은 할당된 자원을 조용히 무시하는 결함이었다.
        UUID token = tokenRegistry.issue(context.server().getId(), axis, Path.of(decided.imagePath()));
        Optional<String> taskPath = context.provider()
                .startFlash(context.target(), axis, tokenRegistry.urlFor(token));
        if (taskPath.isEmpty()) {
            tokenRegistry.revoke(context.server().getId(), axis);
            ledger.failAxis(context.server(), context.progress(), axis, FlashLedger.FLASH_EXCEPTION,
                    "굽기 요청이 받아들여지지 않았습니다", context.now());
            return;
        }
        ledger.openFlash(context.server(), axis, decided, taskPath.get(), context.now());
        log.info("[flash] {} — {} 굽기 시작 : {}", context.server().getId(), axis.label(), decided.display());
    }

    /** 등가 비교 — 정규화만 한다(순서 비교가 아니다). */
    static boolean sameVersion(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}
