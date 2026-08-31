package com.example.serverprovision.provisioning.assignment.service.plan;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 계열 CLI 의 용량 표기를 바이트로 환산하고 지정값과 대조한다(E3.5-2 결정 2) — 표기 단위는 전부 2진으로
 * 해석한다(storcli "446.625 GB" = GiB · sas3ircu "3815447 MB" = MiB, 실측 오차 0.1% 이내가 근거).
 * 정의서 지정값의 10진 해석은 {@code DiskCapacityUnit.toBytes} 가 SSOT 다.
 */
public final class RaidReportedSize {

    /** 인접 판매 계급 간격(480↔500 = 4.2%)보다 좁고 실측 환산 오차(0.1%)보다 30배 넓은 값. */
    public static final double TOLERANCE = 0.03;

    private static final Pattern REPORTED = Pattern.compile(
            "([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(KB|MB|GB|TB)", Pattern.CASE_INSENSITIVE);

    private RaidReportedSize() {
    }

    /** CLI 원문 표기 → 바이트(2진). 해석 불가면 empty — 그 디스크는 계획에서 사유와 함께 제외된다. */
    public static OptionalLong parse(String reported) {
        if (reported == null) {
            return OptionalLong.empty();
        }
        Matcher m = REPORTED.matcher(reported.trim());
        if (!m.matches()) {
            return OptionalLong.empty();
        }
        double value = Double.parseDouble(m.group(1).replace(",", ""));
        long unit = switch (m.group(2).toUpperCase(Locale.ROOT)) {
            case "KB" -> 1L << 10;
            case "MB" -> 1L << 20;
            case "GB" -> 1L << 30;
            default -> 1L << 40;
        };
        return OptionalLong.of((long) (value * unit));
    }

    /** 기준값 대비 상대 오차 ±3% 안이면 같은 용량 계급으로 본다. */
    public static boolean matches(long actualBytes, long referenceBytes) {
        if (referenceBytes <= 0) {
            return false;
        }
        return Math.abs(actualBytes - referenceBytes) <= referenceBytes * TOLERANCE;
    }
}
