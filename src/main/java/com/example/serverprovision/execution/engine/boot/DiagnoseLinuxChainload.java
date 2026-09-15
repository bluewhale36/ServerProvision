package com.example.serverprovision.execution.engine.boot;

/**
 * 진단 Alpine 체인로드 스크립트 빌더(E3.5-1 공용화) — 진단 phase 와 RAID 구성 phase 가 같은 진단
 * 리눅스로 부팅하므로, E1-1 이 {@code DiagnoseLinuxExecutor} 소유로 두었던 text block 을 두 번째
 * 사용처가 생긴 시점에 여기로 추출했다("갈라지는 시점에 분리"). 커널 인자 계약(agent.sh 와의 SSOT):
 * {@code provision_token} · {@code provision_base}. 실패 폴백 = sleep 후 /boot 재진입(UC-4 류).
 * <p>S19-2 — 자산 URL 은 PXE 부팅 credential 이 든 절대 URL({@link PxeBootUrls#assetsBase()})이고, {@code provision_base} 는
 * credential 없이 간다(에이전트는 토큰으로 인증). 재진입은 호출자가 준 절대 credential URL 그대로.</p>
 */
public final class DiagnoseLinuxChainload {

    private DiagnoseLinuxChainload() {
    }

    public static String script(PxeBootUrls urls, String guestToken, String reentryUrl) {
        String assets = urls.assetsBase();
        return """
                #!ipxe
                echo [provision] chainloading diagnose linux...
                kernel %s/vmlinuz-lts ip=dhcp modules=loop,squashfs console=tty0 console=ttyS0,115200 alpine_repo=%s/repo/main modloop=%s/modloop-lts apkovl=%s/diag.apkovl.tar.gz provision_token=%s provision_base=%s initrd=initramfs-lts || goto failed
                initrd %s/initramfs-lts || goto failed
                boot || goto failed
                :failed
                echo [provision] chainload failed. retrying...
                sleep 30
                chain %s
                """.formatted(assets, assets, assets, assets, guestToken, urls.provisionBase(), assets, reentryUrl);
    }
}
