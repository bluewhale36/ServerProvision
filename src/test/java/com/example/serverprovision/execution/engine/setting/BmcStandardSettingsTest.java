package com.example.serverprovision.execution.engine.setting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-2 D-11 — 시간대 파생값은 키를 늘리지 않고 ZoneId 에서 계산한다(AMI 의 utc_minutes · timezone_record 표기). */
class BmcStandardSettingsTest {

    private static final Instant AT = Instant.parse("2026-08-26T03:00:00Z");

    @Test
    @DisplayName("Asia/Seoul → utc_minutes 540 · timezone_record \"Asia/Seoul,GMT+09:00\"(HAR 정본 표기)")
    void seoul() {
        BmcStandardSettings s = BmcSettingItemTest.standard();

        assertThat(s.utcMinutes(AT)).isEqualTo(540);
        assertThat(s.timezoneRecord(AT)).isEqualTo("Asia/Seoul,GMT+09:00");
    }

    @Test
    @DisplayName("음수 · 30분 오프셋도 같은 표기 규칙 — Etc/GMT+5 → GMT-05:00, Asia/Kolkata → GMT+05:30")
    void otherOffsets() {
        BmcStandardSettings west = new BmcStandardSettings("Etc/GMT+5", false, "", "", false, 0, null);
        BmcStandardSettings india = new BmcStandardSettings("Asia/Kolkata", false, "", "", false, 0, null);

        assertThat(west.utcMinutes(AT)).isEqualTo(-300);
        assertThat(west.timezoneRecord(AT)).isEqualTo("Etc/GMT+5,GMT-05:00");
        assertThat(india.utcMinutes(AT)).isEqualTo(330);
        assertThat(india.timezoneRecord(AT)).isEqualTo("Asia/Kolkata,GMT+05:30");
    }
}
