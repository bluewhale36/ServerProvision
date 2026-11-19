package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.management.os.enums.OSName;

/**
 * 정의서가 이 게스트에 정한 Windows 설치 목표(E4-1-a-3) — SPI 구현(provisioning)이 활성 스냅샷에서 파생한다.
 * Windows 정의서가 아니면 공급자가 empty 를 돌려준다(다른 계열은 그 계열 설치기 몫, E4-1-b-4-1).
 *
 * <p>{@link OsTarget} 은 R15-2 드라이버 선별의 OS 축 — 정의서의 OSMetadata 에서 이름 · 버전을 읽는다.
 * 비밀번호를 나르는 record 라 {@code toString} 을 마스킹한다(E2-2 F-4 — 로그로 새던 교훈).</p>
 */
public record WindowsInstallTarget(WindowsImageName imageName, String administratorPassword, OsTarget osTarget) {

    /** 드라이버 선별이 보는 OS 축. 정의서가 OSMetadata 를 가리키지 않으면 {@link #UNKNOWN} — 이름 무관 후보만 남긴다. */
    public record OsTarget(OSName osName, String osVersion) {
        public static final OsTarget UNKNOWN = new OsTarget(null, null);

        public boolean known() {
            return osName != null;
        }
    }

    public WindowsInstallTarget {
        if (osTarget == null) osTarget = OsTarget.UNKNOWN;
    }

    public static WindowsInstallTarget windows(WindowsImageName imageName, String administratorPassword) {
        return new WindowsInstallTarget(imageName, administratorPassword, OsTarget.UNKNOWN);
    }

    public static WindowsInstallTarget windows(WindowsImageName imageName, String administratorPassword, OsTarget osTarget) {
        return new WindowsInstallTarget(imageName, administratorPassword, osTarget);
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
                + ", osTarget=" + osTarget + "]";
    }
}
