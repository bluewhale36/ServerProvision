package com.example.serverprovision.execution.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1-I-2-a CP4 (동반) — 자산 핸들러의 마커 서빙 제외 판정. 사이드카·인트리 마커는 서빙 대상에서 빠지고
 * (게스트에 미노출) 실제 자산 파일은 서빙된다.
 */
class MarkerHiddenResourceResolverTest {

    @Test
    @DisplayName("마커 경로(.provision.json)는 제외 대상 — 사이드카·인트리 모두")
    void markerPaths_excluded() {
        assertThat(PxeAssetsConfig.MarkerHiddenResourceResolver.isMarker("vmlinuz-lts.provision.json")).isTrue();
        assertThat(PxeAssetsConfig.MarkerHiddenResourceResolver.isMarker("diag.apkovl.tar.gz.provision.json")).isTrue();
        assertThat(PxeAssetsConfig.MarkerHiddenResourceResolver.isMarker("repo/.provision.json")).isTrue();
    }

    @Test
    @DisplayName("실제 자산 경로는 서빙 대상 — 제외 아님")
    void assetPaths_served() {
        assertThat(PxeAssetsConfig.MarkerHiddenResourceResolver.isMarker("vmlinuz-lts")).isFalse();
        assertThat(PxeAssetsConfig.MarkerHiddenResourceResolver.isMarker("modloop-lts")).isFalse();
        assertThat(PxeAssetsConfig.MarkerHiddenResourceResolver.isMarker("repo/main/x86_64/dmidecode-3.5-r0.apk")).isFalse();
        assertThat(PxeAssetsConfig.MarkerHiddenResourceResolver.isMarker(null)).isFalse();
    }
}
