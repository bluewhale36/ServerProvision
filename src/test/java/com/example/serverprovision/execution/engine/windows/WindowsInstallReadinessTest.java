package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E4-1-a-3 CP4 — 준비도 진리표 12행(plan §4-2). 게이트 · 대기 스크립트 · 상세 카드가 같은 함수를 보므로 이 표가
 * 곧 세 곳의 문구 계약이다. wire(ASCII) 와 notes(한국어)가 짝으로 나온다.
 */
class WindowsInstallReadinessTest {

    static final WindowsImageName STANDARD = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");
    static final WindowsImageName DATACENTER = new WindowsImageName("Windows Server 2025 SERVERDATACENTER");

    static WindowsImage image(WindowsImageName name, String edition) {
        return new WindowsImage(1, name, name.value() + " (데스크톱 환경)", edition, "Server", "ko-KR", "10.0.26100.1742");
    }

    static InstallSourceSnapshot source() {
        return InstallSourceSnapshot.present(
                List.of(image(STANDARD, "ServerStandard"), image(DATACENTER, "ServerDatacenter")), 1L, Instant.now());
    }

    static WindowsInstallProperties props() {
        return props("/srv/pxe/win2025", "\\\\10.0.0.7\\win2025", "deploy", "s3cret-9x", "KEY-STD", null);
    }

    static WindowsInstallProperties props(String root, String unc, String user, String pw, String std, String dc) {
        return new WindowsInstallProperties(root, unc, user, pw, null, new WindowsInstallProperties.ProductKeys(std, dc));
    }

    static WindowsInstallAssets assets(boolean wimboot, boolean bootWim, boolean setup) {
        return assets(wimboot, bootWim, setup, true);
    }

    /** E4-1-a-4 — 설치 후 스크립트 둘의 존재를 하나의 플래그로(둘 다 있음 / 둘 다 없음). */
    static WindowsInstallAssets assets(boolean wimboot, boolean bootWim, boolean setup, boolean oemScripts) {
        return new WindowsInstallAssets(Path.of("wimboot"), wimboot, Path.of("boot.wim"), bootWim, Path.of("setup.exe"), setup,
                Path.of("$OEM$/$$/Setup/Scripts/SetupComplete.cmd"), oemScripts, Path.of("$OEM$/$1/SPV/spv-report.ps1"), oemScripts);
    }

    static Optional<WindowsInstallTarget> windows() {
        return Optional.of(WindowsInstallTarget.windows(STANDARD, "P@ss"));
    }

    private static PhaseReadiness judge(Optional<WindowsInstallTarget> target) {
        return WindowsInstallReadiness.judge(target, source(), props(), assets(true, true, true));
    }

    @Test
    @DisplayName("1행 — 창 밖(활성 할당 없음 · OS 설치 단계 없음)은 READY (판정 대상 아님)")
    void row1_outsideWindow_isReady() {
        PhaseReadiness r = judge(Optional.empty());
        assertThat(r.grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(r.wire()).isEqualTo("ok");
    }

    @Test
    @DisplayName("12행 — 전부 갖춰지면 READY · notes 비어 있음")
    void row12_allPresent_isReady() {
        PhaseReadiness r = judge(windows());
        assertThat(r.grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(r.notes()).isEmpty();
    }

    @Test
    @DisplayName("2행 — 리눅스 정의서 → BLOCKED · 계열명을 지목 · 정의서 교체 안내")
    void row2_linuxDefinition_isBlocked() {
        PhaseReadiness r = judge(Optional.of(WindowsInstallTarget.unsupported("RHEL 계열")));
        assertThat(r.isBlocked()).isTrue();
        assertThat(r.wire()).isEqualTo("linux install not supported");
        assertThat(r.notes()).singleElement().asString().contains("RHEL 계열 설치는 지원하지 않습니다").contains("Windows 정의서로 교체");
    }

    @Test
    @DisplayName("3 · 4행 — 구 저장본(이미지 · 비밀번호 없음) → 정의서 수정을 지목하는 사유 둘")
    void row3and4_legacyDefinition_pointsToDefinition() {
        PhaseReadiness r = judge(Optional.of(WindowsInstallTarget.windows(null, null)));
        assertThat(r.isBlocked()).isTrue();
        assertThat(r.wire()).isEqualTo("image not chosen; administrator password missing");
        assertThat(r.notes()).allMatch(n -> n.endsWith("정의서를 수정하세요"));
    }

    @Test
    @DisplayName("5행 — 소스 미설정 → 대시보드 영역 지목 · 파일 결손은 따로 나열하지 않는다(재료 자체가 없다)")
    void row5_sourceNotConfigured() {
        PhaseReadiness r = WindowsInstallReadiness.judge(windows(), InstallSourceSnapshot.notConfigured(),
                props("", "\\\\10.0.0.7\\win2025", "deploy", "s3cret-9x", "KEY-STD", null), WindowsInstallAssets.none());
        assertThat(r.wire()).isEqualTo("install source not configured");
        assertThat(r.notes()).singleElement().asString().contains("대시보드 Windows 설치 소스 영역");
    }

    @Test
    @DisplayName("6행 — install.wim 없음 / 읽기 실패는 다른 코드 · 이미지 대조는 건너뛴다")
    void row6_installWimMissingOrUnreadable() {
        PhaseReadiness missing = WindowsInstallReadiness.judge(windows(), InstallSourceSnapshot.missing(), props(), assets(true, true, true));
        assertThat(missing.wire()).isEqualTo("install.wim missing");
        PhaseReadiness unreadable = WindowsInstallReadiness.judge(windows(), InstallSourceSnapshot.unreadable(9L, Instant.now()), props(), assets(true, true, true));
        assertThat(unreadable.wire()).isEqualTo("install.wim unreadable");
    }

    @Test
    @DisplayName("7행 — 선택 이미지가 소스 카탈로그에 없음 → image not in source")
    void row7_imageNotInSource() {
        Optional<WindowsInstallTarget> other = Optional.of(WindowsInstallTarget.windows(new WindowsImageName("Windows Server 2022 SERVERSTANDARD"), "P@ss"));
        PhaseReadiness r = judge(other);
        assertThat(r.wire()).isEqualTo("image not in source");
        assertThat(r.notes()).singleElement().asString().contains("선택한 이미지가 소스 install.wim 에 없습니다");
    }

    @Test
    @DisplayName("8행 — boot.wim · setup.exe · wimboot 는 없는 파일마다 한 줄")
    void row8_eachMissingFileListed() {
        PhaseReadiness r = WindowsInstallReadiness.judge(windows(), source(), props(), assets(false, false, false));
        assertThat(r.wire()).isEqualTo("boot.wim missing; setup.exe missing; wimboot missing");
        assertThat(r.notes()).hasSize(3).anyMatch(n -> n.startsWith("wimboot 없음 — 소스 루트에 배치하세요"));
    }

    @Test
    @DisplayName("9행 — 공유 접속 정보(UNC · 계정 · 비밀번호) 중 하나라도 없으면 환경변수를 지목")
    void row9_shareCredentialsMissing() {
        PhaseReadiness r = WindowsInstallReadiness.judge(windows(), source(),
                props("/srv/pxe/win2025", "\\\\10.0.0.7\\win2025", "deploy", "", "KEY-STD", null), assets(true, true, true));
        assertThat(r.wire()).isEqualTo("share credentials missing");
        assertThat(r.notes()).singleElement().asString().endsWith("환경변수");
    }

    @Test
    @DisplayName("10행 — 공유 비밀번호에 배치 금지 문자(% 등) → 렌더 전에 막는다")
    void row10_sharePasswordBatchUnsafe() {
        PhaseReadiness r = WindowsInstallReadiness.judge(windows(), source(),
                props("/srv/pxe/win2025", "\\\\10.0.0.7\\win2025", "deploy", "pa%ss", "KEY-STD", null), assets(true, true, true));
        assertThat(r.wire()).isEqualTo("share password has batch-unsafe chars");
    }

    @Test
    @DisplayName("11행 — 선택 이미지 edition 의 제품 키 미설정 → edition 을 지목(다른 edition 의 키는 무관)")
    void row11_productKeyMissingForEdition() {
        Optional<WindowsInstallTarget> datacenter = Optional.of(WindowsInstallTarget.windows(DATACENTER, "P@ss"));
        PhaseReadiness r = judge(datacenter);   // props: Standard 키만 있다
        assertThat(r.wire()).isEqualTo("product key missing for ServerDatacenter");
        assertThat(r.notes()).singleElement().isEqualTo("제품 키 ServerDatacenter 미설정 — 환경변수");
    }

    @Test
    @DisplayName("누적 — 사유가 여럿이면 전부 싣고 wire 는 '; ' 로 잇는다(운영자가 한 번에 고친다)")
    void multipleReasonsAccumulate() {
        PhaseReadiness r = WindowsInstallReadiness.judge(Optional.of(WindowsInstallTarget.windows(DATACENTER, null)),
                source(), props("/srv/pxe/win2025", "", "", "", "KEY-STD", null), assets(true, false, true));
        assertThat(r.wire()).isEqualTo("administrator password missing; boot.wim missing; share credentials missing; product key missing for ServerDatacenter");
        assertThat(r.notes()).hasSize(4);
    }

    @Test
    @DisplayName("12행(E4-1-a-4) — 설치 후 스크립트 둘 중 하나라도 없으면 BLOCKED · 사유가 대시보드 조립 버튼을 지목 · wire 'oem scripts not assembled'")
    void oemScriptsMissing_blocked() {
        PhaseReadiness r = WindowsInstallReadiness.judge(windows(), source(), props(), assets(true, true, true, false));

        assertThat(r.isBlocked()).isTrue();
        assertThat(r.wire()).isEqualTo("oem scripts not assembled");
        assertThat(r.notes()).singleElement().asString().contains("설치 후 스크립트 미조립").contains("[드라이버 페이로드 조립]");
        WindowsInstallAssets half = new WindowsInstallAssets(Path.of("wimboot"), true, Path.of("boot.wim"), true, Path.of("setup.exe"), true,
                Path.of("SetupComplete.cmd"), true, Path.of("spv-report.ps1"), false);
        assertThat(WindowsInstallReadiness.judge(windows(), source(), props(), half).wire()).isEqualTo("oem scripts not assembled");
    }

    @Test
    @DisplayName("12행은 소스 미설정이면 따로 나오지 않는다(소스 사유 하나로 충분) · 스크립트가 있으면 READY 그대로")
    void oemScripts_notDoubledWhenUnconfigured() {
        PhaseReadiness unconfigured = WindowsInstallReadiness.judge(windows(), source(),
                props("", "\\\\h\\s", "deploy", "pw", "KEY", null), WindowsInstallAssets.none());
        assertThat(unconfigured.wire()).isEqualTo("install source not configured");
        assertThat(WindowsInstallReadiness.judge(windows(), source(), props(), assets(true, true, true, true)).isBlocked()).isFalse();
    }
}
