package com.example.serverprovision.provisioning.assignment.service.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3.5-2 결정 2 — CLI 표기의 2진 환산과 ±3% 판정. T4 · T5 가 허용률을 양쪽에서 못박는 한 쌍이다:
 * 실측 표기는 지정값과 맞아야 하고, 인접 판매 계급은 들어오면 안 된다.
 */
class RaidReportedSizeTest {

    @Test
    @DisplayName("T4 — 실측 표기 3종의 2진 환산이 지정값(10진) ±3% 안에 든다")
    void realReportedSizes_matchSpecifiedClasses() {
        long spec480 = 480_000_000_000L;
        long spec4T = 4_000_000_000_000L;
        assertThat(RaidReportedSize.matches(RaidReportedSize.parse("446.625 GB").orElseThrow(), spec480)).isTrue();
        assertThat(RaidReportedSize.matches(RaidReportedSize.parse("3.637 TB").orElseThrow(), spec4T)).isTrue();
        assertThat(RaidReportedSize.matches(RaidReportedSize.parse("3815447 MB").orElseThrow(), spec4T)).isTrue();
        assertThat(RaidReportedSize.matches(RaidReportedSize.parse("894.25 GB").orElseThrow(), 960_000_000_000L)).isTrue();
    }

    @Test
    @DisplayName("T5 — 인접 판매 계급(500 · 512 실물)은 480 GB 지정에 매칭되지 않는다")
    void adjacentClasses_doNotMatch() {
        long spec480 = 480_000_000_000L;
        // 500 GB 실물(10진 500e9 를 2진 표기로) = 465.661 GiB, 512 GB 실물 = 476.837 GiB
        assertThat(RaidReportedSize.matches(RaidReportedSize.parse("465.661 GB").orElseThrow(), spec480)).isFalse();
        assertThat(RaidReportedSize.matches(500_000_000_000L, spec480)).isFalse();
        assertThat(RaidReportedSize.matches(512_000_000_000L, spec480)).isFalse();
    }

    @Test
    @DisplayName("±3% 경계 — 2.9% 는 안, 3.1% 는 밖")
    void toleranceBoundary() {
        long ref = 480_000_000_000L;
        assertThat(RaidReportedSize.matches((long) (ref * 1.029), ref)).isTrue();
        assertThat(RaidReportedSize.matches((long) (ref * 0.971), ref)).isTrue();
        assertThat(RaidReportedSize.matches((long) (ref * 1.031), ref)).isFalse();
        assertThat(RaidReportedSize.matches((long) (ref * 0.969), ref)).isFalse();
    }

    @Test
    @DisplayName("단위 4종 전부 2진 계수 — KB · MB · GB · TB (콤마 · 소수 허용)")
    void parse_binaryUnits() {
        assertThat(RaidReportedSize.parse("1024 KB")).hasValue(1024L << 10);
        assertThat(RaidReportedSize.parse("3815447 MB")).hasValue(3815447L << 20);
        assertThat(RaidReportedSize.parse("3,815,447 MB")).hasValue(3815447L << 20);
        assertThat(RaidReportedSize.parse("100 GB")).hasValue(100L << 30);
        assertThat(RaidReportedSize.parse("2 TB")).hasValue(2L << 40);
        assertThat(RaidReportedSize.parse("  446.625 GB  ")).isPresent();
    }

    @Test
    @DisplayName("해석 불가 표기와 0 이하 기준값은 정직하게 실패한다")
    void parse_rejectsUnknownForms() {
        assertThat(RaidReportedSize.parse(null)).isEmpty();
        assertThat(RaidReportedSize.parse("")).isEmpty();
        assertThat(RaidReportedSize.parse("n/a")).isEmpty();
        assertThat(RaidReportedSize.parse("12 PB")).isEmpty();
        assertThat(RaidReportedSize.parse("GB 480")).isEmpty();
        assertThat(RaidReportedSize.matches(480_000_000_000L, 0L)).isFalse();
    }
}
