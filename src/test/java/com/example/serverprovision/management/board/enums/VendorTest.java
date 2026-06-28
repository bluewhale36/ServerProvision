package com.example.serverprovision.management.board.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U1 D12 — iPXE 보고 모델명 정규화. Gigabyte 는 {@code -000} 접미사만 보수적으로 제거, 그 외 제조사는 항등(미관측 규약 추측 금지).
 */
class VendorTest {

    @Test
    @DisplayName("Gigabyte — 끝의 -000 접미사 제거 (MS03-CE0-000 → MS03-CE0)")
    void gigabyte_stripsSuffix() {
        assertThat(Vendor.GIGABYTE.canonicalizeReportedModel("MS03-CE0-000")).isEqualTo("MS03-CE0");
        assertThat(Vendor.GIGABYTE.canonicalizeReportedModel("MS73-HB1-000")).isEqualTo("MS73-HB1");
    }

    @Test
    @DisplayName("Gigabyte — 접미사 없으면 그대로(trim 만)")
    void gigabyte_noSuffix_unchanged() {
        assertThat(Vendor.GIGABYTE.canonicalizeReportedModel("MS03-CE0")).isEqualTo("MS03-CE0");
        assertThat(Vendor.GIGABYTE.canonicalizeReportedModel("  MS03-CE0-000  ")).isEqualTo("MS03-CE0");
    }

    @Test
    @DisplayName("Gigabyte — 중간의 -000 은 건드리지 않음(끝 접미사만)")
    void gigabyte_onlyTrailingSuffix() {
        assertThat(Vendor.GIGABYTE.canonicalizeReportedModel("MX-000-CE0")).isEqualTo("MX-000-CE0");
    }

    @Test
    @DisplayName("비 Gigabyte — 항등(미관측 제조사 규약을 추측하지 않음)")
    void nonGigabyte_identity() {
        assertThat(Vendor.ASUS.canonicalizeReportedModel("P13R-E-000")).isEqualTo("P13R-E-000");
        assertThat(Vendor.FUJITSU.canonicalizeReportedModel("D3934-B1-000")).isEqualTo("D3934-B1-000");
    }

    @Test
    @DisplayName("null 안전")
    void nullSafe() {
        assertThat(Vendor.GIGABYTE.canonicalizeReportedModel(null)).isNull();
        assertThat(Vendor.ASUS.canonicalizeReportedModel(null)).isNull();
    }
}
