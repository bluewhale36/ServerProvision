package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.osinstall.OsVolumeLocator;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.service.InstallTreeManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * RHEL 준비도 판정의 재료를 모은다(E4-1-b-4-2) — 정의서 해석(b-3), PXE 서버 주소, 설치 트리 사전 점검, 대상 디스크.
 * 설치기(게이트 · 서빙)와 서버 상세 카드가 같은 조립을 써서 "화면은 준비됐다는데 게이트가 막는다" 는 어긋남이 없다.
 */
@Component
@RequiredArgsConstructor
public class RhelInstallReadinessResolver {

    private final RhelInstallationResolutionProvider provider;
    private final InstallTreeManager treeManager;
    private final OsVolumeLocator osVolumeLocator;
    private final RhelInstallTokenRegistry tokenRegistry;

    /** 한 게스트의 판정 결과. */
    public record Resolved(RhelResolution resolution, PhaseReadiness readiness, RhelDiskSelection.Selection disk) {
    }

    /** 활성 정의서의 OS 설치 단계가 RHEL 계열인가 — 판정 없이 해석만 본다(설치기 선택용). */
    public boolean claims(UUID guestServerId) {
        return provider.resolveFor(guestServerId).isPresent();
    }

    /** empty = 창 밖(활성 할당 없음 · OS 설치 단계 없음 · RHEL 정의서 아님). */
    public Optional<Resolved> resolve(UUID guestServerId) {
        return provider.resolveFor(guestServerId).map(resolution -> {
            Optional<TreeFailureReason> treeProblem = resolution.ready()
                    .flatMap(ready -> treeManager.inspect(ready.media().isoId()));
            RhelDiskSelection.Selection disk = RhelDiskSelection.judge(osVolumeLocator.locate(guestServerId));
            PhaseReadiness readiness = RhelInstallReadiness.judge(resolution, !tokenRegistry.baseUrl().isEmpty(), treeProblem, disk);
            return new Resolved(resolution, readiness, disk);
        });
    }

    public PhaseReadiness readiness(UUID guestServerId) {
        return resolve(guestServerId).map(Resolved::readiness).orElseGet(PhaseReadiness::ready);
    }
}
