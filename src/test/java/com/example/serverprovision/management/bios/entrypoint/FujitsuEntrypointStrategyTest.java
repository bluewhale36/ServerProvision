package com.example.serverprovision.management.bios.entrypoint;

import com.example.serverprovision.management.bios.exception.EntrypointDetectionNotSupportedException;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S5-11 v2 — Fujitsu 진입점 탐지 정책 placeholder. 본 슬라이스는 explicit unsupported.
 */
class FujitsuEntrypointStrategyTest {

    private final FujitsuEntrypointStrategy strategy = new FujitsuEntrypointStrategy();

    @Test
    @DisplayName("supports : FUJITSU 만 true")
    void supportsFujitsuOnly() {
        assertThat(strategy.supports(Vendor.FUJITSU)).isTrue();
        assertThat(strategy.supports(Vendor.ASUS)).isFalse();
        assertThat(strategy.supports(Vendor.GIGABYTE)).isFalse();
    }

    @Test
    @DisplayName("detect : 항상 EntrypointDetectionNotSupportedException throw — Notion 'Fujitsu 제외' 정책")
    void detectAlwaysUnsupported(@TempDir Path tmp) {
        assertThatThrownBy(() -> strategy.detect(tmp, null))
                .isInstanceOf(EntrypointDetectionNotSupportedException.class)
                .hasMessageContaining("Fujitsu");
    }
}
