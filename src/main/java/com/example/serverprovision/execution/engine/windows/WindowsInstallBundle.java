package com.example.serverprovision.execution.engine.windows;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 토큰 하나가 가리키는 파일 다섯 — 정적 둘은 경로(디스크 스트리밍), 렌더본 셋은 문자열(메모리에만 존재 · 토큰 회수와 함께 소멸).
 * 렌더본에 비밀값(공유 비밀번호 · Base64 비밀번호)이 들어 있으므로 이 객체는 로그 · 원장 어디에도 싣지 않는다.
 */
public record WindowsInstallBundle(
        Path wimboot,
        Path bootWim,
        String winpeshlIni,
        String installBat,
        String autounattendXml
) {

    public Optional<Path> pathOf(WindowsInstallFile file) {
        return switch (file) {
            case WIMBOOT -> Optional.ofNullable(wimboot);
            case BOOT_WIM -> Optional.ofNullable(bootWim);
            default -> Optional.empty();
        };
    }

    public Optional<String> textOf(WindowsInstallFile file) {
        return switch (file) {
            case WINPESHL -> Optional.ofNullable(winpeshlIni);
            case INSTALL_BAT -> Optional.ofNullable(installBat);
            case AUTOUNATTEND -> Optional.ofNullable(autounattendXml);
            default -> Optional.empty();
        };
    }

    @Override
    public String toString() {
        return "WindowsInstallBundle[wimboot=" + wimboot + ", bootWim=" + bootWim + ", rendered=****]";
    }
}
