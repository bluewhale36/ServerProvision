package com.example.serverprovision.domain.os.model.installation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneTest {

    @Test
    @DisplayName("isUTC=true이면 스크립트에 --isUtc 플래그 포함")
    void scriptContainsIsUtcFlag_whenUTC() {
        Timezone tz = Timezone.builder().timezone("Asia/Seoul").isUTC(true).build();

        String script = tz.getRHELScript();

        assertThat(script).contains("--isUtc");
    }

    @Test
    @DisplayName("isUTC=false이면 --isUtc, --utc 모두 미포함")
    void scriptNotContainsAnyUtcFlag_whenNotUTC() {
        Timezone tz = Timezone.builder().timezone("Asia/Seoul").isUTC(false).build();

        String script = tz.getRHELScript();

        assertThat(script).doesNotContain("--isUtc");
        assertThat(script).doesNotContain("--utc");
    }

    @Test
    @DisplayName("스크립트에 타임존 문자열 포함")
    void scriptContainsTimezoneValue() {
        Timezone tz = Timezone.builder().timezone("America/New_York").isUTC(false).build();

        String script = tz.getRHELScript();

        assertThat(script).contains("timezone America/New_York");
    }
}
