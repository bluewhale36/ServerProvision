package com.example.serverprovision.execution.vo;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 게스트 서버가 인식된 시점을 묶는 상대 시간 (U3-3 DEC-F · 2026-08-11 눈금 확정).
 *
 * <p>경과 시간을 <b>내림</b>해 같은 값끼리 한 묶음으로 본다. 눈금은 경과가 커질수록 성겨진다 —
 * 1분 안은 10초, 1시간 안은 1분, 24시간 안은 1시간, 7일 안은 1일, 그 뒤는 1주다.</p>
 *
 * <p>이렇게 가르는 이유는 <b>운영자가 구분해야 하는 폭이 시간대마다 다르기 때문</b>이다.
 * 입고 직후에는 10초 차이가 배치를 가르지만, 일주일 지난 서버를 하루 단위로 흩어 놓아도
 * 볼 것이 늘지 않는다. 눈금이 하나면 최근은 뭉치고 과거는 흩어진다.</p>
 *
 * <p>눈금이 도메인 규칙이므로 조회 서비스나 뷰에 흩뜨리지 않고 이 값 객체가 소유한다.</p>
 */
public record RegistrationAge(Unit unit, long amount) implements Comparable<RegistrationAge> {

    /**
     * 표시 눈금. {@code step} 은 내림 폭(초), {@code seconds} 는 단위 1개의 길이(초)다 —
     * 10초 눈금처럼 둘이 다른 경우가 있어 나눠 둔다.
     */
    public enum Unit {
        SECOND(10L, 1L, "초"),
        MINUTE(60L, 60L, "분"),
        HOUR(3600L, 3600L, "시간"),
        DAY(86_400L, 86_400L, "일"),
        WEEK(604_800L, 604_800L, "주");

        private final long step;
        private final long seconds;
        private final String suffix;

        Unit(long step, long seconds, String suffix) {
            this.step = step;
            this.seconds = seconds;
            this.suffix = suffix;
        }
    }

    private static final long MINUTE_SECONDS = 60L;
    private static final long HOUR_SECONDS = 3_600L;
    private static final long DAY_SECONDS = 86_400L;
    private static final long WEEK_SECONDS = 604_800L;

    /**
     * 등록 시각과 기준 시각으로 묶음을 정한다.
     * 미래 시각(시계 어긋남)은 0초로 흡수한다 — 목록에 음수 경과를 내보내느니 방금 들어온 것으로 본다.
     */
    public static RegistrationAge of(LocalDateTime registeredAt, LocalDateTime now) {
        long elapsed = Duration.between(registeredAt, now).getSeconds();
        if (elapsed < 0) {
            elapsed = 0;
        }
        if (elapsed < MINUTE_SECONDS) {
            return floored(Unit.SECOND, elapsed);
        }
        if (elapsed < HOUR_SECONDS) {
            return floored(Unit.MINUTE, elapsed);
        }
        if (elapsed < DAY_SECONDS) {
            return floored(Unit.HOUR, elapsed);
        }
        if (elapsed < WEEK_SECONDS) {
            return floored(Unit.DAY, elapsed);
        }
        return floored(Unit.WEEK, elapsed);
    }

    /** 경과를 그 눈금의 폭으로 내림해 단위 개수로 바꾼다. */
    private static RegistrationAge floored(Unit unit, long elapsedSeconds) {
        long steps = elapsedSeconds / unit.step;
        return new RegistrationAge(unit, steps * unit.step / unit.seconds);
    }

    /** 화면에 그대로 쓰는 문구 — "30초 전" · "12분 전" · "3시간 전" · "2일 전" · "5주 전". */
    public String getDescription() {
        if (unit == Unit.SECOND && amount == 0) {
            return "방금";
        }
        return amount + unit.suffix + " 전";
    }

    /** 정렬 기준 — 내림된 경과 초. 최근일수록 앞이다. */
    public long elapsedSeconds() {
        return amount * unit.seconds;
    }

    @Override
    public int compareTo(RegistrationAge o) {
        return Long.compare(elapsedSeconds(), o.elapsedSeconds());
    }
}
