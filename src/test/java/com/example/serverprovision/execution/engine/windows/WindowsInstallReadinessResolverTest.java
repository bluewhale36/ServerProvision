package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.FakeWim;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** E4-1-a-3 CP4 — 준비도 재료 조립: 실 카탈로그(가짜 WIM) · 실 소스 자산 · SPI mock 으로 실행기와 카드가 같은 판정을 받는지. */
class WindowsInstallReadinessResolverTest {

    private static final UUID GUEST_ID = UUID.randomUUID();

    @TempDir Path root;

    private final WindowsInstallationResolutionProvider provider = mock(WindowsInstallationResolutionProvider.class);

    private WindowsInstallReadinessResolver resolver(String datacenterKey) {
        WindowsInstallProperties props = new WindowsInstallProperties(root.toString(), "\\\\10.0.0.5\\win2025", "deploy",
                "share-secret-9x", null, new WindowsInstallProperties.ProductKeys("KEY-STD", datacenterKey));
        return new WindowsInstallReadinessResolver(provider, new WindowsImageCatalog(props), props, new WindowsInstallSource(props));
    }

    @Test
    @DisplayName("창 밖 — SPI empty 면 resolve empty · readiness READY")
    void outsideWindow() {
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.empty());

        assertThat(resolver(null).resolve(GUEST_ID)).isEmpty();
        assertThat(resolver(null).readiness(GUEST_ID).grade()).isEqualTo(ReadinessGrade.READY);
    }

    @Test
    @DisplayName("Windows 목표 + 소스 · wimboot · 키 → READY · 이미지(표시명)가 함께 실린다")
    void windowsTarget_ready() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);   // E4-1-a-4 — 12행(스크립트 부재)이 판정을 가리지 않게
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));

        WindowsInstallReadinessResolver.Resolved r = resolver(null).resolve(GUEST_ID).orElseThrow();

        assertThat(r.readiness().grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(r.image()).hasValueSatisfying(i -> assertThat(i.editionId()).isEqualTo("ServerStandard"));
        assertThat(r.snapshot().ready()).isTrue();
    }

    @Test
    @DisplayName("wimboot 부재 → BLOCKED 'wimboot missing' — 파일 관측이 판정에 실제로 들어간다")
    void wimbootMissing_blocked() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);   // E4-1-a-4 — 12행(스크립트 부재)이 판정을 가리지 않게
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));

        assertThat(resolver(null).readiness(GUEST_ID).wire()).isEqualTo("wimboot missing");
    }

    @Test
    @DisplayName("리눅스 목표 → BLOCKED · 이미지 없음(대조할 이름이 없다)")
    void linuxTarget_blocked() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);   // E4-1-a-4 — 12행(스크립트 부재)이 판정을 가리지 않게
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(WindowsInstallTarget.unsupported("RHEL 계열")));

        WindowsInstallReadinessResolver.Resolved r = resolver(null).resolve(GUEST_ID).orElseThrow();

        assertThat(r.readiness().wire()).isEqualTo("linux install not supported");
        assertThat(r.image()).isEmpty();
    }
}
