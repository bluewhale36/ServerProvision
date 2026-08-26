package com.example.serverprovision.execution.engine.setting;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 사내 표준 BMC 세팅 값(E3-2 D-1 · D-11) — {@code provision.bmc.standard.*}. 정의서가 아니라 시스템 설정이 공급하는
 * 이유는 표준값이 정의서마다 복제되면 drift 의 원천이 되기 때문이다. 시간대 파생값(utc_minutes · timezone_record)은
 * 키를 늘리지 않고 {@link ZoneId} 에서 계산한다.
 *
 * @param bond Network Bond — 값은 보드 공통이나 인터페이스명({@code ifc})은 보드마다 같은지 재실측 항목(T3)이라 설정으로 받는다
 */
@ConfigurationProperties(prefix = "provision.bmc.standard")
public record BmcStandardSettings(
        String timezone,
        boolean ntpAuto,
        String primaryNtp,
        String secondaryNtp,
        boolean coldRedundantEnable,
        int masterPsu,
        Bond bond
) {

    public record Bond(boolean enable, String mode, String ifc, boolean autoConfiguration) {
    }

    /** 표준 시간대의 UTC 오프셋(분) — AMI 의 {@code utc_minutes}. */
    public int utcMinutes(Instant at) {
        return offsetAt(at).getTotalSeconds() / 60;
    }

    /** AMI 의 {@code timezone_record} 표기("Asia/Seoul,GMT+09:00"). */
    public String timezoneRecord(Instant at) {
        int total = offsetAt(at).getTotalSeconds() / 60;
        int hours = total / 60;
        int minutes = Math.abs(total % 60);
        return String.format("%s,GMT%+03d:%02d", timezone, hours, minutes);
    }

    private ZoneOffset offsetAt(Instant at) {
        return ZoneId.of(timezone).getRules().getOffset(at);
    }
}
