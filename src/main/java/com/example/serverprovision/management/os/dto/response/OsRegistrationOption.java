package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.enums.RhelRelease;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * OS 등록 화면의 OS 선택지 한 줄(E4-1-b-2 D-21). 지원 메이저가 있는 OS(Rocky Linux)는 메이저마다 한 줄이고
 * ("Rocky Linux 9"), 나머지는 OS 이름 한 줄이다. 메이저 목록의 정본은 {@link RhelRelease} — 상수를 더하면 선택지가 따라 는다.
 *
 * @param release 지원 메이저가 없는 OS 면 {@code null}
 */
public record OsRegistrationOption(OSName osName, RhelRelease release) {

    public String label() {
        return release == null ? osName.getDisplayName() : release.displayName();
    }

    /** 고르면 버전 칸에 미리 채울 앞자리("9.") — 메이저가 없으면 빈 문자열. */
    public String versionPrefix() {
        return release == null ? "" : release.major() + ".";
    }

    public static List<OsRegistrationOption> of(List<OSName> names) {
        return names.stream()
                .flatMap(name -> {
                    List<RhelRelease> releases = Arrays.stream(RhelRelease.values()).filter(r -> r.osName() == name).toList();
                    return releases.isEmpty()
                            ? Stream.of(new OsRegistrationOption(name, null))
                            : releases.stream().map(r -> new OsRegistrationOption(name, r));
                })
                .toList();
    }
}
