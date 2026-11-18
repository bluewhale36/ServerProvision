package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.management.os.enums.RhelRelease;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 정의서에서 온 kickstart 재료(해석이 끝난 값) — 비밀번호는 crypt, 환경 · 그룹은 카탈로그 코드, 파티션은 kickstart 토큰과 MiB.
 * 실행 때만 아는 값(설치 원본 URL · 대상 디스크)은 {@code KickstartRuntime} 이 따로 나른다.
 *
 * @param environmentCode 저장 코드 그대로(comps 추출이 {@code ^} 를 붙여 저장한다 — 렌더는 {@code @} 만 앞에 붙인다)
 */
public record RhelInstallTarget(
        RhelRelease release,
        String timezone,
        boolean utc,
        List<KickstartPartition> partitions,
        Optional<String> rootCrypt,
        boolean allowSshRoot,
        List<KickstartUser> users,
        String environmentCode,
        List<String> groupCodes,
        boolean kdump
) {

    public RhelInstallTarget {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(environmentCode, "environmentCode");
        Objects.requireNonNull(rootCrypt, "rootCrypt");
        partitions = List.copyOf(partitions);
        users = List.copyOf(users);
        groupCodes = List.copyOf(groupCodes);
    }

    @Override
    public String toString() {
        return "RhelInstallTarget[release=" + release + ", timezone=" + timezone + ", utc=" + utc
                + ", partitions=" + partitions + ", rootCrypt=" + (rootCrypt.isPresent() ? "****" : "locked")
                + ", allowSshRoot=" + allowSshRoot + ", users=" + users + ", environmentCode=" + environmentCode
                + ", groupCodes=" + groupCodes + ", kdump=" + kdump + "]";
    }
}
