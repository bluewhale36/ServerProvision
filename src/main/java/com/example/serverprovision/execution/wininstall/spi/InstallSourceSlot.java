package com.example.serverprovision.execution.wininstall.spi;

import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;

import java.nio.file.Path;

/**
 * Windows 설치 소스의 관측 슬롯 6종 — 추출 절차(런북 §14-1)가 항상 만드는 {@code sources/} 아래 파일 셋과 소스 루트의 wimboot,
 * 그리고 앱의 조립 액션이 만드는 {@code sources/$OEM$} 의 설치 후 스크립트 둘(E4-1-a-4).
 * 실측에서 루트에 복사해 둔 boot.wim 은 편의였고, 정본 위치는 {@code sources/boot.wim} 이다. E4-1-a-3 의 토큰 서빙과
 * 준비도가 같은 경로를 본다(경로 정본은 이 enum 하나). wimboot 는 ipxe.org 서명 릴리스를 운영 절차가 루트에 둔다(런북 §14-4).
 */
public enum InstallSourceSlot implements SystemAssetSlot {

    BOOT_WIM("WinPE 부팅 이미지 (boot.wim)", "sources/boot.wim", "netboot 아티팩트", Cadence.SOURCE),
    INSTALL_WIM("설치 이미지 (install.wim)", "sources/install.wim", "설치 소스", Cadence.SOURCE),
    SETUP_EXE("Windows Setup (setup.exe)", "sources/setup.exe", "설치 소스", Cadence.SOURCE),
    WIMBOOT("wimboot 부트로더 (wimboot)", "wimboot", "netboot 아티팩트", Cadence.SOURCE),
    /** E4-1-a-4 — 조립 액션이 만든다. Setup 이 {@code $$} → {@code %WINDIR%} 로 복사해 첫 로그온 전에 실행한다. */
    OEM_SETUPCOMPLETE("설치 후 스크립트 (SetupComplete.cmd)", "sources/$OEM$/$$/Setup/Scripts/SetupComplete.cmd",
            "설치 후 페이로드", Cadence.OEM),
    /** E4-1-a-4 — 조립 액션이 만든다. Setup 이 {@code $1} → 시스템 드라이브 루트로 복사해 첫 로그온이 실행한다. */
    OEM_REPORT("완료 보고 스크립트 (spv-report.ps1)", "sources/$OEM$/$1/SPV/spv-report.ps1", "설치 후 페이로드", Cadence.OEM);

    /** enum 상수는 자기 클래스의 static 필드를 앞서 참조할 수 없어(forward reference) 홀더에 둔다. */
    private static final class Cadence {
        static final String SOURCE = "Windows 버전 교체 시 (런북 §14-1 추출 절차)";
        static final String OEM = "드라이버 자원 변경 시 (대시보드 [드라이버 페이로드 조립])";
    }

    private final String label;
    private final String relativePath;
    private final String category;
    private final String replaceCadence;

    InstallSourceSlot(String label, String relativePath, String category, String replaceCadence) {
        this.label = label;
        this.relativePath = relativePath;
        this.category = category;
        this.replaceCadence = replaceCadence;
    }

    public Path resolve(Path sourceRoot) {
        return sourceRoot.resolve(relativePath);
    }

    @Override
    public String slotKey() {
        return name();
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String filename() {
        return relativePath;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public String layoutLabel() {
        return "단일 파일";
    }

    /** 운영 절차가 통째로 교체한다 — UI 업로드 교체 대상이 아니다. */
    @Override
    public boolean replaceable() {
        return false;
    }

    @Override
    public String replaceCadence() {
        return replaceCadence;
    }
}
