package com.example.serverprovision.execution.wininstall.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-a-2 CP4 — 전역 운영 설정 record 의 판정 메서드(미설정 표현 · 공유 셋 · 시간대 기본값 · 에디션별 키). */
class WindowsInstallPropertiesTest {

    @Test
    @DisplayName("source-root 비공백 = configured · 공유는 UNC · 계정 · 비밀번호 셋 다 있어야 configured")
    void configured() {
        WindowsInstallProperties unset = new WindowsInstallProperties(" ", null, null, null, null, null);
        assertThat(unset.configured()).isFalse();
        assertThat(unset.sourceRootPath()).isEmpty();
        assertThat(unset.shareConfigured()).isFalse();
        assertThat(unset.effectiveTimeZone()).isEqualTo(WindowsInstallProperties.DEFAULT_TIME_ZONE);

        WindowsInstallProperties half = new WindowsInstallProperties("/srv/pxe/win2025", "\\\\10.0.0.5\\win2025", "deploy", "", " ", null);
        assertThat(half.configured()).isTrue();
        assertThat(half.sourceRootPath()).isPresent();
        assertThat(half.shareConfigured()).isFalse();

        WindowsInstallProperties full = new WindowsInstallProperties("/srv/pxe/win2025", "\\\\10.0.0.5\\win2025", "deploy", "pw", "Korea Standard Time", null);
        assertThat(full.shareConfigured()).isTrue();
        assertThat(full.effectiveTimeZone()).isEqualTo("Korea Standard Time");
    }

    @Test
    @DisplayName("제품 키 — EDITIONID 대소문자 무시 · 빈 값 = 미설정 · 모르는 에디션 = 미설정 · null 안전")
    void productKeys() {
        WindowsInstallProperties.ProductKeys keys = new WindowsInstallProperties.ProductKeys("AAAAA-BBBBB", " ");
        assertThat(keys.forEdition("ServerStandard")).contains("AAAAA-BBBBB");
        assertThat(keys.forEdition("serverstandard")).contains("AAAAA-BBBBB");
        assertThat(keys.forEdition("ServerDatacenter")).isEmpty();
        assertThat(keys.forEdition("ServerAzure")).isEmpty();
        assertThat(keys.forEdition(null)).isEmpty();

        WindowsInstallProperties none = new WindowsInstallProperties("/srv", null, null, null, null, null);
        assertThat(none.productKeysOrEmpty().forEdition("ServerStandard")).isEmpty();
    }
}
