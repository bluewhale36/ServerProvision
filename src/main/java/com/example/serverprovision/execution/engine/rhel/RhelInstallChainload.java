package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.installtree.enums.InstallMediaLayout;

/**
 * RHEL 설치 체인 iPXE 스크립트(E4-1-b-4-2). 토큰 트리 URL 아래의 설치 커널과 초기 램디스크를 받아 kickstart 설치를 시작한다.
 * 커널 인자 {@code initrd=} 는 UEFI 에서 커널이 램디스크를 찾게 하는 이름이다. 각 줄이 실패하면 잠시 쉬고 /boot 로 돌아간다
 * (Windows 체인과 같은 폴백).
 */
public final class RhelInstallChainload {

    private RhelInstallChainload() {
    }

    /**
     * @param treeUrl      설치 트리 URL(토큰 경로)
     * @param kickstartUrl kickstart URL(토큰 경로)
     * @param osLabel      콘솔에 보일 OS 이름(예 "Rocky Linux 9.5")
     */
    public static String script(String treeUrl, String kickstartUrl, String osLabel, long isoId, String reentryUrl) {
        return """
                #!ipxe
                echo [provision] rhel install: %s (iso %d)
                echo [provision] this server: ip=${ip} mac=${mac} uuid=${uuid}
                kernel %s/%s initrd=initrd.img inst.repo=%s inst.ks=%s inst.text ip=dhcp || goto failed
                initrd %s/%s || goto failed
                boot || goto failed
                :failed
                echo [provision] rhel install chain failed. retrying...
                sleep 30
                chain %s
                """.formatted(osLabel, isoId, treeUrl, InstallMediaLayout.RHEL_TREE.kernel(), treeUrl, kickstartUrl,
                treeUrl, InstallMediaLayout.RHEL_TREE.initrd(), reentryUrl);
    }
}
