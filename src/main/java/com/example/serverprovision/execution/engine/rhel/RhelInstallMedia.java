package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.enums.OSName;

import java.util.Objects;

/**
 * 정의서가 고른 설치 원본 — ISO 와 그 ISO 가 등록된 OS(E4-1-b-4-2). 설치 트리를 확보하는 열쇠이고, 확보한 트리가 정의서와 같은
 * 배포판 · 주 버전인지 대조하는 기준이다.
 *
 * @param osLabel 등록 OS 의 화면 이름(예 "Rocky Linux 9.5") — 화면 · 원장 meta 에 쓴다
 */
public record RhelInstallMedia(long isoId, OSName osName, String osVersion, String osLabel) {

    public RhelInstallMedia {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(osVersion, "osVersion");
        Objects.requireNonNull(osLabel, "osLabel");
    }

    /** 트리가 이 원본과 같은 배포판 · 주 버전인가 — 규칙은 ISO 등록 때와 같다(b-4 Q4). */
    public boolean matches(TreeRelease tree) {
        return tree.matches(osName, osVersion);
    }
}
