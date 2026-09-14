package com.example.serverprovision.management.subprogram.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 변형 진입점의 설치 방식(R15-1 D-4) — 별도 선택 필드 없이 진입점 파일의 확장자가 정한다.
 * 실행 명령(pnputil · msiexec · 직접 실행)의 렌더는 R15-2 가 constant 별 메서드로 붙인다.
 */
public enum InstallEntrypointKind {
    INF("INF", "inf"),
    MSI("MSI", "msi"),
    EXE("EXE", "exe");

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
        int slash = name.lastIndexOf('/');
        String file = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = file.lastIndexOf('.');
        if (dot < 0 || dot == file.length() - 1) {
            return Optional.empty();
        }
        String ext = file.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (InstallEntrypointKind kind : values()) {
            if (kind.extension.equals(ext)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
