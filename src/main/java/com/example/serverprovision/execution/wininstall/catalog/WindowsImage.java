package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;

/**
 * install.wim 의 이미지 1건 — XML 의 {@code IMAGE} 요소에서 읽은 메타.
 *
 * @param installationType {@code WINDOWS/INSTALLATIONTYPE} — "Server"(데스크톱 환경) 또는 "Server Core"
 * @param language         {@code WINDOWS/LANGUAGES/DEFAULT} — 응답 파일의 UI 언어를 이 값에서 파생한다(E4-1-a-3)
 * @param build            {@code WINDOWS/VERSION} 을 MAJOR.MINOR.BUILD.SPBUILD 로 이은 것
 */
public record WindowsImage(
        int index,
        WindowsImageName name,
        String displayName,
        String editionId,
        String installationType,
        String language,
        String build
) {

    private static final String DESKTOP_EXPERIENCE_TYPE = "Server";

    public boolean desktopExperience() {
        return DESKTOP_EXPERIENCE_TYPE.equals(installationType);
    }
}
