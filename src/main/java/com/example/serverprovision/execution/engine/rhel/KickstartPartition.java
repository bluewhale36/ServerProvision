package com.example.serverprovision.execution.engine.rhel;

import java.util.Objects;

/**
 * kickstart {@code part} 한 줄의 재료. fsType 은 이미 kickstart 토큰(efi · xfs …)이고, sizeMiB 는 MiB 로 환산된 값이다
 * (grow 이고 크기 0 이면 1 — {@code --size=1 --grow}).
 */
public record KickstartPartition(String mountPoint, String fsType, long sizeMiB, boolean grow) {

    public KickstartPartition {
        Objects.requireNonNull(mountPoint, "mountPoint");
        Objects.requireNonNull(fsType, "fsType");
        if (sizeMiB < 1) {
            throw new IllegalArgumentException("sizeMiB 는 1 이상이어야 한다: " + mountPoint);
        }
    }
}
