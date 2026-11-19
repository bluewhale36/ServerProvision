package com.example.serverprovision.execution.installtree.vo;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.vo.TreeInfo;

import java.util.Objects;

/**
 * 설치 트리의 {@code .treeinfo} 가 밝히는 배포판 이름과 버전(예: Rocky Linux · 9.5).
 * 주 버전은 kickstart 생성(b-3)이 버전별 구현을 고르는 키다.
 */
public record TreeRelease(String name, String version) {

    public TreeRelease {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (name.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("배포판 이름과 버전은 비어 있을 수 없다");
        }
    }

    /** 버전의 첫 마디(9.5 → 9). 숫자가 아니면 -1. */
    public int majorVersion() {
        String head = version.split("\\.", 2)[0];
        try {
            return Integer.parseInt(head);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 등록 OS 와 같은 배포판 · 같은 주 버전인가 — ISO 등록 때 쓰는 {@link TreeInfo#matches} 를 그대로 쓴다(설치기 본체 위치는 대조와 무관). */
    public boolean matches(OSName osName, String osVersion) {
        return new TreeInfo(name, version, TreeInfo.DEFAULT_STAGE2).matches(osName, osVersion);
    }

    public String label() {
        return name + " " + version;
    }
}
