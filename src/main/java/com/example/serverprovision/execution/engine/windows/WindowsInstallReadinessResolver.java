package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 준비도 판정의 재료 조립(E4-1-a-3) — 정의서 목표(SPI) · 소스 스냅샷 · 운영 설정 · 자산 존재를 한 번 읽어
 * {@link WindowsInstallReadiness#judge} 에 넘긴다. 실행기(게이트 · 서빙)와 상세 카드가 같은 조립을 쓰므로
 * "화면이 준비됐다는데 게이트가 막는다" 는 어긋남이 없다.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallReadinessResolver {

    private final WindowsInstallationResolutionProvider provider;
    private final WindowsImageCatalog catalog;
    private final WindowsInstallProperties properties;
    private final WindowsInstallSource source;

    /** 한 게스트의 해석 결과 — {@code image} 는 목표 이미지가 소스에 실재할 때만 채워진다. */
    public record Resolved(WindowsInstallTarget target, InstallSourceSnapshot snapshot,
                           Optional<WindowsImage> image, PhaseReadiness readiness) {
    }

    /** empty = 창 밖(활성 할당 없음 · OS 설치 단계 없음). */
    public Optional<Resolved> resolve(UUID guestServerId) {
        Optional<WindowsInstallTarget> target = provider.resolveFor(guestServerId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        InstallSourceSnapshot snapshot = catalog.snapshot();
        Optional<WindowsImage> image = target.get().hasImage() ? snapshot.find(target.get().imageName()) : Optional.empty();
        PhaseReadiness readiness = WindowsInstallReadiness.judge(target, snapshot, properties, source.assets());
        return Optional.of(new Resolved(target.get(), snapshot, image, readiness));
    }

    public PhaseReadiness readiness(UUID guestServerId) {
        return resolve(guestServerId).map(Resolved::readiness).orElseGet(PhaseReadiness::ready);
    }
}
