package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 펌웨어 갱신 phase 실행기(E2-1-b) — 이 슬라이스에서는 <b>무엇을 어느 버전으로 구울지 정하는 데까지</b>가
 * 책임이다. 실제 flash 는 E2-2(BIOS) · E2-3(BMC) 가 이 골격 위에 얹는다.
 *
 * <p>빈으로 등록되는 순간 dispatch 매트릭스의 "미구현 phase HOLD" 행이 이 실행기 위임으로 바뀐다 —
 * 신규 phase 지원이 분기 추가가 아니라 빈 등록이라는 SPI 계약(DEC-6)의 두 번째 실물이다.</p>
 */
@Component
@RequiredArgsConstructor
public class FirmwareUpdatingExecutor implements ProvisioningPhaseExecutor {

    private final FirmwareResolutionProvider firmwareResolutionProvider;

    @Override
    public ProvisioningPhase phase() {
        return ProvisioningPhase.FIRMWARE_UPDATING;
    }

    /**
     * 진입 준비도 — 해석을 부수효과 없이 한 번 돌려 본 결과가 곧 판정이다(E2-1-b D-1). 별도의 검증
     * 로직을 새로 짓지 않으므로 "화면이 경고한 것 = 실행이 거절한 것" 이 구조로 보장된다.
     * 활성 할당이 없거나 정의서에 펌웨어 갱신 단계가 없으면 판정 대상이 아니라 준비됨으로 본다.
     */
    @Override
    public PhaseReadiness readiness(GuestServer server, ProvisioningProgress progress) {
        return firmwareResolutionProvider.resolveFor(server.getId())
                .map(FirmwareResolution::toReadiness)
                .orElseGet(PhaseReadiness::ready);
    }

    /**
     * 해석은 끝났고 굽는 엔진은 아직 없다 — 조용히 통과시키지 않고 그 사실을 게스트 콘솔에 남기며
     * 대기시킨다. 결손 대기(HOLD)와는 다른 상태다: 여기 도달했다는 것은 재료가 온전하다는 뜻이다.
     */
    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        String summary = firmwareResolutionProvider.resolveFor(server.getId())
                .map(FirmwareResolution::wireSummary)
                .orElse("no target");
        return IpxeScripts.awaitingFirmwareFlash(summary, rebootQuery);
    }
}
