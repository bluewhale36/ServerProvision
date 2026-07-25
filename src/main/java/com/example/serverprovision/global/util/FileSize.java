package com.example.serverprovision.global.util;

/**
 * 바이트 크기를 사람이 읽는 표시로(예: {@code 198.4 MB}). 대시보드 슬롯 크기와 버전 목록 크기가 동일하게
 * 표시되도록 공용화한다(E1-I-2-b-2 에서 {@code DiagnosticAssetIntegrityService} 의 private 구현을 승격).
 */
public final class FileSize {

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    private FileSize() {
    }

    public static String format(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int i = -1;
        do {
            value /= 1024;
            i++;
        } while (value >= 1024 && i < UNITS.length - 1);
        return String.format("%.1f %s", value, UNITS[i]);
    }
}
