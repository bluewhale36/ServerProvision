package com.example.serverprovision.execution.engine.rhel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-4-2 — 설치 체인: 토큰 트리의 커널 · 램디스크, inst.repo · inst.ks, 실패하면 /boot 로 돌아간다. */
class RhelInstallChainloadTest {

    @Test
    @DisplayName("커널 인자에 initrd= · inst.repo · inst.ks · inst.text 가 실리고 각 줄은 실패 폴백을 가진다")
    void script() {
        String tree = "http://pxe:8080/api/pxe/v1/rhel/t/tree";
        String ks = "http://pxe:8080/api/pxe/v1/rhel/t/ks.cfg";
        String script = RhelInstallChainload.script(tree, ks, "Rocky Linux 9.5", 12L, "http://pxe:8080/api/pxe/v1/boot?mac=x");

        assertThat(script).startsWith("#!ipxe\n")
                .contains("echo [provision] rhel install: Rocky Linux 9.5 (iso 12)")
                .contains("kernel " + tree + "/images/pxeboot/vmlinuz initrd=initrd.img inst.repo=" + tree
                        + " inst.ks=" + ks + " inst.text ip=dhcp || goto failed")
                .contains("initrd " + tree + "/images/pxeboot/initrd.img || goto failed")
                .contains("boot || goto failed")
                .contains("chain http://pxe:8080/api/pxe/v1/boot?mac=x");
    }
}
