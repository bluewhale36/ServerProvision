package com.example.serverprovision.execution.engine.windows;

/**
 * wimboot 체인 iPXE 스크립트(E4-1-a-3 §1-8 win.ipxe) — 토큰 번들 URL 아래의 파일 다섯을 initrd 로 얹고 boot.
 * 각 줄 {@code || goto failed} 로 실패를 잡아 sleep 후 /boot 재진입(진단 체인로드와 같은 폴백).
 */
public final class WindowsInstallChainload {

    private WindowsInstallChainload() {
    }

    public static String script(String bundleUrl, String imageName, String rebootQuery) {
        return """
                #!ipxe
                echo [provision] windows install: %s
                echo [provision] this server: ip=${ip} mac=${mac} uuid=${uuid}
                kernel %s/wimboot || goto failed
                initrd %s/winpeshl.ini winpeshl.ini || goto failed
                initrd %s/install.bat install.bat || goto failed
                initrd %s/autounattend.xml autounattend.xml || goto failed
                initrd %s/boot.wim boot.wim || goto failed
                boot || goto failed
                :failed
                echo [provision] windows chainload failed. retrying...
                sleep 30
                chain /api/pxe/v1/boot?%s
                """.formatted(imageName, bundleUrl, bundleUrl, bundleUrl, bundleUrl, bundleUrl, rebootQuery);
    }
}
