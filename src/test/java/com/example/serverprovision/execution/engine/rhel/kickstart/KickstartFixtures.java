package com.example.serverprovision.execution.engine.rhel.kickstart;

import com.example.serverprovision.execution.engine.rhel.KickstartPartition;
import com.example.serverprovision.execution.engine.rhel.KickstartUser;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;
import com.example.serverprovision.management.os.enums.RhelRelease;

import java.util.List;
import java.util.Optional;

/** 골든 파일 3 개의 입력. 해시는 고정 예시 문자열(실제 계산 아님). CP5 ksvalidator 하네스도 같은 입력을 쓴다. */
public final class KickstartFixtures {

    static final String ROOT_CRYPT = "$6$exRootSalt$exampleRootCryptNotComputed";
    static final String USER_CRYPT = "$6$exUserSalt$exampleUserCryptNotComputed";
    static final KickstartRuntime RUNTIME = new KickstartRuntime(
            "http://pxe.example/api/pxe/v1/rhel/TOKEN/tree", new InstallDisk("wwn-0x5000c500a1b2c3d4"));

    private KickstartFixtures() {
    }

    static List<KickstartPartition> basicPreset() {
        return List.of(
                new KickstartPartition("/boot/efi", "efi", 1024, false),
                new KickstartPartition("/boot", "ext4", 1024, false),
                new KickstartPartition("swap", "swap", 16384, false),
                new KickstartPartition("/", "ext4", 1, true));
    }

    static List<KickstartPartition> xfsPreset() {
        return List.of(
                new KickstartPartition("/boot/efi", "efi", 600, false),
                new KickstartPartition("/boot", "xfs", 1024, false),
                new KickstartPartition("swap", "swap", 8192, false),
                new KickstartPartition("/", "xfs", 1, true),
                new KickstartPartition("/var", "xfs", 51200, false));
    }

    /** Rocky 9 기본: root 있음 · sudo 사용자 · 기본 프리셋 · UTC · KDump 켬 · 그룹 하나. */
    public static RhelInstallTarget rocky9() {
        return new RhelInstallTarget(RhelRelease.R9, "Asia/Seoul", true, basicPreset(),
                Optional.of(ROOT_CRYPT), false,
                List.of(new KickstartUser("admin", Optional.of(USER_CRYPT), true)),
                "^server-product-environment", List.of("development"), true);
    }

    /** Rocky 10: root SSH 허용 · 그룹 둘. */
    public static RhelInstallTarget rocky10() {
        return new RhelInstallTarget(RhelRelease.R10, "Asia/Seoul", true, basicPreset(),
                Optional.of(ROOT_CRYPT), true,
                List.of(new KickstartUser("admin", Optional.of(USER_CRYPT), true)),
                "^server-product-environment", List.of("development", "system-tools"), true);
    }

    /** Rocky 8: root 잠금 · sudo 사용자 + 비밀번호 없는 사용자 · XFS 프리셋 · UTC 끔 · KDump 끔 · 그룹 없음. */
    public static RhelInstallTarget rocky8() {
        return new RhelInstallTarget(RhelRelease.R8, "Asia/Seoul", false, xfsPreset(),
                Optional.empty(), false,
                List.of(new KickstartUser("admin", Optional.of(USER_CRYPT), true),
                        new KickstartUser("viewer", Optional.empty(), false)),
                "^minimal-environment", List.of(), false);
    }
}
