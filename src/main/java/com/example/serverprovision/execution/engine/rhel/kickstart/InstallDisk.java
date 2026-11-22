package com.example.serverprovision.execution.engine.rhel.kickstart;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 설치 대상 디스크의 {@code /dev/disk/by-id} 이름(예 {@code wwn-0x5000c500a1b2c3d4}). 값은 b-6 이 OS 영역 볼륨에서 정한다.
 * kickstart 의 {@code ignoredisk} · {@code clearpart} · {@code part --ondisk} 가 같은 경로를 쓴다.
 */
public record InstallDisk(String byIdName) {

    private static final Pattern BY_ID_NAME = Pattern.compile("^[A-Za-z0-9._:-]+$");

    public InstallDisk {
        Objects.requireNonNull(byIdName, "byIdName");
        if (!BY_ID_NAME.matcher(byIdName).matches()) {
            throw new IllegalArgumentException("by-id 이름 형식이 아니다: " + byIdName);
        }
    }

    public String byIdPath() {
        return "disk/by-id/" + byIdName;
    }
}
