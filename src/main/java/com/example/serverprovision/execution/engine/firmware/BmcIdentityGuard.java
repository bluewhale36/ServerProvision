package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.engine.firmware.step.FlashContext;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 신원 확인 관문(E2-2 D-11) — <b>되돌릴 수 없는 조작을 내기 직전</b>(전원 끄기 · 굽기 · 전원 켜기)과
 * <b>그 읽기가 종결 판정의 근거가 될 때</b>(반영 확인) 이 관문을 지난다. 진행 관측(Task 폴링)은 지나지 않는다.
 *
 * <p>막으려는 것은 이렇다. BMC 주소는 진단이 한 번 수집한 값이고 갱신 경로가 없는데, BMC 는 펌웨어를 구운 뒤
 * 스스로 재기동하며 사라졌다 돌아오고 DHCP 에 고정 예약이 없어 그때 다른 주소를 받을 수 있다.
 * 그 주소를 다른 게스트의 BMC 가 쓰고 있다면 <b>남의 장비를 굽거나 끄게 된다.</b></p>
 *
 * <p>불일치와 도달 불가를 다르게 다루는 것이 요점이다 — 도달하지 못하는 것은 <b>상태</b>라 기다리면
 * 풀릴 수 있지만, 다른 장비가 답하는 것은 <b>사건</b>이라 즉시 멈춰야 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmcIdentityGuard {

    private final BmcIdentityProbe identityProbe;
    private final FlashTimeoutPolicy timeoutPolicy;
    private final FlashLedger ledger;

    /**
     * 확인되면 참. 아니면 <b>그 아래 호출을 내지 않도록</b> 거짓을 돌려주고, 필요한 처리(즉시 실패 ·
     * 주소 갱신 · 시한 만료)를 여기서 마친다.
     *
     * @param axis 시한을 재는 기준 축 — 없으면 복귀 시한을 쓴다
     */
    public boolean confirm(FlashContext ctx, FirmwareAxis axis) {
        // 판정과 주소 재발견은 phase 무관 Probe(E3-1 에서 추출)가, 원장 기록은 여기가 맡는다.
        BmcIdentity identity = identityProbe.probe(ctx.provider(), ctx.target(),
                ctx.detail().getBoardSerial(), ctx.detail(), "flash");
        if (identity == BmcIdentity.MATCHED) {
            return true;
        }
        if (identity == BmcIdentity.MISMATCHED) {
            log.error("[flash] {} — 신원 불일치, 집행 중단(남의 장비일 수 있다)", ctx.server().getId());
            ledger.failAtCursor(ctx.server(), ctx.progress(), FlashLedger.IDENTITY_MISMATCH,
                    "응답한 장비의 보드 시리얼이 이 서버와 다릅니다", ctx.now());
            return false;
        }
        Duration limit = axis == null ? timeoutPolicy.returnLimit() : timeoutPolicy.limitFor(axis);
        if (timeoutPolicy.isExpired(ctx.progress().getLastTransitionAt(), limit, ctx.now())) {
            ledger.failAtCursor(ctx.server(), ctx.progress(), FlashLedger.BMC_UNREACHABLE,
                    "BMC 에 닿지 못했고 새 주소도 찾지 못했습니다", ctx.now());
        }
        return false;
    }
}
