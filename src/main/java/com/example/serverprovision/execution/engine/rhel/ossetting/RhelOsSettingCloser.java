package com.example.serverprovision.execution.engine.rhel.ossetting;

import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.rhel.RhelInstallationResolutionProvider;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 첫 부팅 보고로 OS 설치를 닫은 뒤 커서가 OS 후처리에 섰으면 그 자리에서 판정해 OS 후처리도 닫는다(E4-1-b-7). 설정은 설치 중에
 * kickstart 로 적용됐고 설치 뒤 서버는 PXE 를 다시 타지 않으므로, 보고 한 번에 열고 닫는다. 판정 기준은 kickstart 를 만든 것과 같은
 * 할당 스냅숏이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RhelOsSettingCloser {

    private final RhelInstallationResolutionProvider resolutionProvider;
    private final OsSettingLedger ledger;
    private final PhaseCursorAdvancer cursorAdvancer;

    /** 커서가 OS 후처리가 아니면(정의서에 그 단계가 없음) 아무것도 하지 않는다. 호출자의 트랜잭션에 참여한다. */
    public void closeIfReached(GuestServer server, ProvisioningProgress progress, Optional<OsSettingObservation> observation,
                               LocalDateTime now) {
        if (progress.isCompleted() || progress.currentPhase() != ProvisioningPhase.OS_SETTING) {
            return;
        }
        OsSettingVerdict verdict = OsSettingVerdict.judge(planOf(server), observation);
        ledger.record(server, verdict, now);
        if (verdict.passed()) {
            cursorAdvancer.advanceOrComplete(progress, server.getId(), now);
        } else {
            progress.markFailed(now);
        }
        log.info("[OS-SETTING] {} — {} : {}", server.getId(), verdict.passed() ? "성공" : "실패", verdict.detail());
    }

    /** 커서가 OS 후처리에 섰다면 할당에 그 단계가 있다는 뜻이라, 목표가 없으면 데이터 손상이다. */
    private RhelOsSettingPlan planOf(GuestServer server) {
        return resolutionProvider.osSettingFor(server.getId())
                .orElseThrow(() -> new IllegalStateException("OS 후처리 목표를 읽을 수 없습니다. guestServerId=" + server.getId()));
    }
}
