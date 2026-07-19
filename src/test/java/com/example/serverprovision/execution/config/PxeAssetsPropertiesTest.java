package com.example.serverprovision.execution.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-1 CP4 — 자산 서빙 설정의 기동 fail-fast 계약(plan §7). 반쪽 설정(자산은 켰는데 콜백 주소 없음 /
 * 조립 전 디렉토리)은 조용히 운행하지 않는다.
 */
class PxeAssetsPropertiesTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("assets.root 설정 + base-url 공백 → 기동 실패 (반쪽 설정 차단)")
    void blankBaseUrl_failsFast() {
        assertThatThrownBy(() -> new PxeAssetsProperties(tempDir.toString(), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.server.base-url");
    }

    @Test
    @DisplayName("자산 디렉토리 부재 → 기동 실패 + 조립 스크립트 안내")
    void missingRootDirectory_failsFast() {
        assertThatThrownBy(() -> new PxeAssetsProperties(
                tempDir.resolve("not-built-yet").toString(), "http://10.0.2.2:7777"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("build-assets.sh");
    }

    @Test
    @DisplayName("base-url 뒤 슬래시 정규화 — URL 조립 시 이중 슬래시 방지")
    void trailingSlash_normalized() {
        PxeAssetsProperties properties =
                new PxeAssetsProperties(tempDir.toString(), "http://10.0.2.2:7777/");
        assertThat(properties.getBaseUrl()).isEqualTo("http://10.0.2.2:7777");
        assertThat(properties.getRoot()).isEqualTo(tempDir.toAbsolutePath().normalize());
    }
}
