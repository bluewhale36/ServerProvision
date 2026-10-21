package com.example.serverprovision.execution.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R17 — 미채집 원인 판정 진리표(관측한 커널 드라이버 → 안내 4종) 전수 + 관측값 정규화.
 */
class RaidCollectionNoticeTest {

    @Test
    @DisplayName("관측 없음(옛 보고 · 옛 에이전트) → UNKNOWN — 종전 HF22 문구 키")
    void unknown() {
        assertThat(RaidCollectionNotice.of(null)).isEqualTo(RaidCollectionNotice.UNKNOWN);
        assertThat(RaidCollectionNotice.UNKNOWN.messageKey()).isEqualTo("execution.raid-inventory.uncollected");
    }

    @Test
    @DisplayName("계열 드라이버(megaraid_sas · mpt3sas) → SUPPORTED_NOT_COLLECTED")
    void supported() {
        assertThat(RaidCollectionNotice.of(new KernelDriverObservation("megaraid_sas")))
                .isEqualTo(RaidCollectionNotice.SUPPORTED_NOT_COLLECTED);
        assertThat(RaidCollectionNotice.of(new KernelDriverObservation("mpt3sas")))
                .isEqualTo(RaidCollectionNotice.SUPPORTED_NOT_COLLECTED);
    }

    @Test
    @DisplayName("계열 밖 드라이버(mpi3mr) → UNSUPPORTED_CONTROLLER")
    void unsupportedController() {
        assertThat(RaidCollectionNotice.of(new KernelDriverObservation("mpi3mr")))
                .isEqualTo(RaidCollectionNotice.UNSUPPORTED_CONTROLLER);
    }

    @Test
    @DisplayName("관측했지만 바인딩 없음(빈 문자열 · 공백) → DRIVER_NOT_BOUND — 커널이 모르는 칩")
    void driverNotBound() {
        assertThat(RaidCollectionNotice.of(new KernelDriverObservation(""))).isEqualTo(RaidCollectionNotice.DRIVER_NOT_BOUND);
        assertThat(RaidCollectionNotice.of(new KernelDriverObservation("  "))).isEqualTo(RaidCollectionNotice.DRIVER_NOT_BOUND);
        assertThat(new KernelDriverObservation(" megaraid_sas ").driver()).isEqualTo("megaraid_sas");
        assertThat(new KernelDriverObservation(null).isBound()).isFalse();
    }
}
