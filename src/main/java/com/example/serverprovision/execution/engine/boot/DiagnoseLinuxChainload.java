package com.example.serverprovision.execution.engine.boot;

/**
 * 진단 Alpine 체인로드 스크립트 빌더(E3.5-1 공용화) — 진단 phase 와 RAID 구성 phase 가 같은 진단
 * 리눅스로 부팅하므로, E1-1 이 {@code DiagnoseLinuxExecutor} 소유로 두었던 text block 을 두 번째
 * 사용처가 생긴 시점에 여기로 추출했다("갈라지는 시점에 분리"). 커널 인자 계약(agent.sh 와의 SSOT):
 * {@code provision_token} · {@code provision_base}. 실패 폴백 = sleep 후 /boot 재진입(UC-4 류).
 */
public final class DiagnoseLinuxChainload {

    private DiagnoseLinuxChainload() {
    }

    public static String script(String baseUrl, String guestToken, String rebootQuery) {
        String assets = baseUrl + "/api/pxe/v1/assets";
        return """
                #!ipxe
                echo [provision] chainloading diagnose linux...
                kernel %s/vmlinuz-lts ip=dhcp modules=loop,squashfs console=tty0 console=ttyS0,115200 alpine_repo=%s/repo/main modloop=%s/modloop-lts apkovl=%s/diag.apkovl.tar.gz provision_token=%s provision_base=%s initrd=initramfs-lts || goto failed
                initrd %s/initramfs-lts || goto failed
                boot || goto failed
                :failed
                echo [provision] chainload failed. retrying...
                sleep 30
                chain /api/pxe/v1/boot?%s
                """.formatted(assets, assets, assets, assets, guestToken, baseUrl, assets, rebootQuery);
    }
}
