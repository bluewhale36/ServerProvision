package com.example.serverprovision.execution.engine.osinstall;

import java.util.Locale;

/**
 * 설치가 끝난 뒤의 사후 확증 결과(설치 디스크 · 대상 서버). 어긋나도 설치는 끝났으므로 실패로 바꾸지 않고 사실만 남긴다.
 * 원장 meta 에는 {@link #metaValue()} 의 Boolean(true 확증 · false 어긋남 · null 미보고)으로 적어 옛 행과 형식이 같다.
 */
public enum InstallConfirmation {

    CONFIRMED(Boolean.TRUE, "일치"), MISMATCH(Boolean.FALSE, "불일치"), UNREPORTED(null, "미보고");

    private final Boolean metaValue;
    private final String description;

    InstallConfirmation(Boolean metaValue, String description) {
        this.metaValue = metaValue;
        this.description = description;
    }

    /** meta 의 Boolean 을 옮긴다. null 은 미보고다. */
    public static InstallConfirmation of(Boolean confirmed) {
        if (confirmed == null) {
            return UNREPORTED;
        }
        return confirmed ? CONFIRMED : MISMATCH;
    }

    /** 기대값과 보고값을 대소문자를 무시해 비교한다. 어느 한쪽이라도 비어 있으면 미보고다. */
    public static InstallConfirmation matching(String expected, String reported) {
        if (isBlank(expected) || isBlank(reported)) {
            return UNREPORTED;
        }
        return expected.trim().toLowerCase(Locale.ROOT).equals(reported.trim().toLowerCase(Locale.ROOT)) ? CONFIRMED : MISMATCH;
    }

    /** 원장 meta 에 적을 값. */
    public Boolean metaValue() {
        return metaValue;
    }

    /** 화면 배지 · 원장 운영 기록에 쓰는 이름. */
    public String getDescription() {
        return description;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
