package com.example.serverprovision.execution.wininstall.spi;

import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;

import java.nio.file.Path;

/**
 * Windows 설치 소스의 관측 슬롯 4종 — 추출 절차(런북 §14-1)가 항상 만드는 {@code sources/} 아래 파일 셋과 소스 루트의 wimboot.
 * 실측에서 루트에 복사해 둔 boot.wim 은 편의였고, 정본 위치는 {@code sources/boot.wim} 이다. E4-1-a-3 의 토큰 서빙과
 * 준비도가 같은 경로를 본다(경로 정본은 이 enum 하나). wimboot 는 ipxe.org 서명 릴리스를 운영 절차가 루트에 둔다(런북 §14-4).
 */
public enum InstallSourceSlot implements SystemAssetSlot {

    BOOT_WIM("WinPE 부팅 이미지 (boot.wim)", "sources/boot.wim", "netboot 아티팩트"),
    INSTALL_WIM("설치 이미지 (install.wim)", "sources/install.wim", "설치 소스"),
    SETUP_EXE("Windows Setup (setup.exe)", "sources/setup.exe", "설치 소스"),
    WIMBOOT("wimboot 부트로더 (wimboot)", "wimboot", "netboot 아티팩트");

    private static final String REPLACE_CADENCE = "Windows 버전 교체 시 (런북 §14-1 추출 절차)";

    private final String label;
    private final String relativePath;
    private final String category;

    InstallSourceSlot(String label, String relativePath, String category) {
        this.label = label;
        this.relativePath = relativePath;
        this.category = category;
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
        return REPLACE_CADENCE;
    }
}
