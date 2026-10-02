package com.example.serverprovision.management.subprogram.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 변형 진입점의 설치 방식(R15-1 D-4) — 별도 선택 필드 없이 진입점 경로가 정한다: 파일이면 확장자, 폴더면 끝의 {@code /}
 * (HF23 — 저장 시 실제 디렉토리면 끝에 {@code /} 를 붙여 정규화한다). 실행 명령의 렌더는 R15-2 · HF23 이 맡는다.
 */
public enum InstallEntrypointKind {
    INF("INF", "inf"),
    MSI("MSI", "msi"),
    EXE("EXE", "exe"),
    /** 폴더 아래 INF 를 하나씩 설치(HF23) — 패키지 안의 OS 폴더(예: {@code PRO1000/Winx64/WS2025/})로 범위를 좁힌다. */
    FOLDER("폴더", null);

    private final String label;
    private final String extension;

    InstallEntrypointKind(String label, String extension) {
        this.label = label;
        this.extension = extension;
    }

    public String label() {
        return label;
    }

    public String extension() {
        return extension;
    }

    /** 상대 경로의 확장자로 판별 — 허용 확장자가 아니면 empty. */
    public static Optional<InstallEntrypointKind> fromPath(String relativePath) {
        if (relativePath == null) {
            return Optional.empty();
        }
        String name = relativePath.trim().replace('\\', '/');
        if (name.endsWith("/")) {
            return Optional.of(FOLDER);
        }
        int slash = name.lastIndexOf('/');
        String file = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = file.lastIndexOf('.');
        if (dot < 0 || dot == file.length() - 1) {
            return Optional.empty();
        }
        String ext = file.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (InstallEntrypointKind kind : values()) {
            if (ext.equals(kind.extension)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
