package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.engine.boot.IpxeScripts;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
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
     * 이 phase 에서 게스트가 하는 일은 <b>없다</b> — 굽는 것은 서버가 BMC 에게 시키고, 그동안 게스트는
     * 꺼져 있다(E2-2 D-3). 그래서 여기서 주는 것은 언제나 대기 스크립트이며, 어느 대기인지만 갈린다.
     *
     * <p>집행에 착수한 게스트({@code STEP_RUNNING})가 이 자리에 왔다는 것은 <b>전원이 다시 들어와
     * 돌아왔다</b>는 뜻이다 — 그 재진입이 곧 "POST 를 지났다" 는 신호이고, 서버는 그때부터 인벤토리를
     * 읽어 반영을 확인한다. 아직 착수 전이면 무엇을 구울지만 알려 주고 워커를 기다린다.</p>
     */
    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        if (progress.getMotion() == ProvisioningMotion.STEP_RUNNING) {
            return IpxeScripts.awaitingFirmwareVerification(rebootQuery);
        }
        String summary = firmwareResolutionProvider.resolveFor(server.getId())
                .map(FirmwareResolution::wireSummary)
                .orElse("no target");
        return IpxeScripts.awaitingFirmwareFlash(summary, rebootQuery);
    }
}
