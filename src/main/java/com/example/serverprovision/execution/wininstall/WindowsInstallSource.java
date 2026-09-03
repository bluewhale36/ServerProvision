package com.example.serverprovision.execution.wininstall;

import com.example.serverprovision.execution.engine.windows.WindowsInstallAssets;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.execution.wininstall.spi.InstallSourceSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 설치 소스 루트에서 정적 자산의 경로 · 존재를 산출한다(E4-1-a-3) — 경로 정본은 {@link InstallSourceSlot} 하나다
 * (대시보드 슬롯과 서빙 번들이 같은 위치를 본다). wimboot 는 실측이 소스 루트에 두고 성공했으므로 그 자리다(D-5).
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallSource {

    private final WindowsInstallProperties properties;

    public WindowsInstallAssets assets() {
        Path root = properties.sourceRootPath().orElse(null);
        if (root == null) {
            return WindowsInstallAssets.none();
        }
        Path wimboot = InstallSourceSlot.WIMBOOT.resolve(root);
        Path bootWim = InstallSourceSlot.BOOT_WIM.resolve(root);
        Path setupExe = InstallSourceSlot.SETUP_EXE.resolve(root);
        Path setupComplete = InstallSourceSlot.OEM_SETUPCOMPLETE.resolve(root);
        Path report = InstallSourceSlot.OEM_REPORT.resolve(root);
        return new WindowsInstallAssets(wimboot, Files.isRegularFile(wimboot),
                bootWim, Files.isRegularFile(bootWim), setupExe, Files.isRegularFile(setupExe),
                setupComplete, Files.isRegularFile(setupComplete), report, Files.isRegularFile(report));
    }

    /** wimboot 의 SHA-256(16진) — 런북 §14-4 의 서명 릴리스 해시와 눈으로 대조하는 chip 재료. 없거나 못 읽으면 empty. */
    public Optional<String> wimbootSha256() {
        WindowsInstallAssets assets = assets();
        if (!assets.wimbootPresent()) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(assets.wimboot())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return Optional.of(HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }
}
