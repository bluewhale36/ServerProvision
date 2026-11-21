package com.example.serverprovision.execution.engine.rhel.kickstart;

import com.example.serverprovision.execution.vo.GuestToken;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 첫 부팅 완료 보고 유닛이 kickstart 에 싣는 재료(E4-1-b-5). 값은 env 파일에 작은따옴표로 들어가므로 한 줄에 공백 · 따옴표가 없어야 한다.
 * {@link #toString()} 은 토큰을 가린다.
 *
 * @param consoleSerial 콘솔 배너에 쓰는 보드 시리얼. {@link #consoleSerialOf} 로 정리한 값
 */
public record FirstBootReport(String reportUrl, GuestToken guestToken, UUID expectedSystemUuid, String consoleSerial,
                              String detailUrl) {

    static final String REPORT_PATH = "/api/pxe/v1/agent/rhel/complete";
    private static final Pattern CONSOLE_UNSAFE = Pattern.compile("[^A-Za-z0-9._-]");
    private static final int CONSOLE_SERIAL_MAX = 64;

    public FirstBootReport {
        Objects.requireNonNull(guestToken, "guestToken");
        Objects.requireNonNull(expectedSystemUuid, "expectedSystemUuid");
        KickstartToken.check(reportUrl);
        KickstartToken.check(guestToken.value());
        KickstartToken.check(consoleSerial);
        KickstartToken.check(detailUrl);
    }

    /** 앱 기준 주소에서 보고 주소와 서버 상세 주소를 만든다. */
    public static FirstBootReport of(String baseUrl, GuestToken guestToken, UUID systemUuid, String boardSerial,
                                     UUID guestServerId) {
        return new FirstBootReport(baseUrl + REPORT_PATH, guestToken, systemUuid, consoleSerialOf(boardSerial),
                baseUrl + "/provisioning/server/" + guestServerId);
    }

    /** 콘솔이 그릴 수 있는 글자만 남긴다. 그 밖의 글자는 '_', 64 자에서 자르고, 비면 '-'. */
    public static String consoleSerialOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        String safe = CONSOLE_UNSAFE.matcher(raw.strip()).replaceAll("_");
        return safe.length() > CONSOLE_SERIAL_MAX ? safe.substring(0, CONSOLE_SERIAL_MAX) : safe;
    }

    @Override
    public String toString() {
        return "FirstBootReport[reportUrl=" + reportUrl + ", guestToken=***, expectedSystemUuid=" + expectedSystemUuid
                + ", consoleSerial=" + consoleSerial + ", detailUrl=" + detailUrl + "]";
    }
}
