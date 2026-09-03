package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;

/**
 * 정의서가 이 게스트에 정한 Windows 설치 목표(E4-1-a-3) — SPI 구현(provisioning)이 활성 스냅샷에서 파생한다.
 * 리눅스 계열 정의서는 {@link #unsupported} 로 나른다 — "OS 설치 단계는 있으나 Windows 가 아니다" 는
 * "단계 자체가 없다"(empty) 와 다른 사실이라 준비도가 사유를 지목할 수 있어야 한다.
 *
 * <p>비밀번호를 나르는 record 라 {@code toString} 을 마스킹한다(E2-2 F-4 — 로그로 새던 교훈).</p>
 */
public record WindowsInstallTarget(WindowsImageName imageName, String administratorPassword, String unsupportedFamily) {

    public static WindowsInstallTarget windows(WindowsImageName imageName, String administratorPassword) {
        return new WindowsInstallTarget(imageName, administratorPassword, null);
    }

    public static WindowsInstallTarget unsupported(String familyDescription) {
        return new WindowsInstallTarget(null, null, familyDescription == null ? "리눅스" : familyDescription);
    }

    public boolean windows() {
        return unsupportedFamily == null;
    }

    public boolean hasImage() {
        return imageName != null;
    }

    public boolean hasPassword() {
        return administratorPassword != null && !administratorPassword.isBlank();
    }

    @Override
    public String toString() {
        return "WindowsInstallTarget[imageName=" + imageName + ", password=" + (hasPassword() ? "****" : "(none)")
                + ", unsupportedFamily=" + unsupportedFamily + "]";
    }
}
