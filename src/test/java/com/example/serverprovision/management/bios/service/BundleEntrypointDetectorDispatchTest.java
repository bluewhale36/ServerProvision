package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.entrypoint.EntrypointDetectionStrategy;
import com.example.serverprovision.management.bios.exception.EntrypointStrategyMissingException;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * S5-11 v2 — BundleEntrypointDetector dispatcher 회귀.
 *
 * <p>각 strategy 의 내부 로직은 vendor 별 strategy test 가 검증. 본 test 는 dispatcher 의
 * "vendor 매칭 strategy 찾아 detect 위임" 책임만 검증.</p>
 */
class BundleEntrypointDetectorDispatchTest {

    @Test
    @DisplayName("ASUS vendor : Asus strategy 의 detect 호출 + 반환값 그대로")
    void dispatchesToAsus() {
        EntrypointDetectionStrategy asus = mock(EntrypointDetectionStrategy.class);
        EntrypointDetectionStrategy gigabyte = mock(EntrypointDetectionStrategy.class);
        given(asus.supports(Vendor.ASUS)).willReturn(true);
        given(gigabyte.supports(Vendor.ASUS)).willReturn(false);
        given(asus.detect(Path.of("/tmp"), null)).willReturn("X99.CAP");

        BundleEntrypointDetector detector = new BundleEntrypointDetector(List.of(asus, gigabyte));

        String result = detector.detect(Vendor.ASUS, Path.of("/tmp"), null);

        assertThat(result).isEqualTo("X99.CAP");
        verify(asus).detect(Path.of("/tmp"), null);
    }

    @Test
    @DisplayName("GIGABYTE vendor : Gigabyte strategy 위임")
    void dispatchesToGigabyte() {
        EntrypointDetectionStrategy asus = mock(EntrypointDetectionStrategy.class);
        EntrypointDetectionStrategy gigabyte = mock(EntrypointDetectionStrategy.class);
        given(asus.supports(Vendor.GIGABYTE)).willReturn(false);
        given(gigabyte.supports(Vendor.GIGABYTE)).willReturn(true);
        given(gigabyte.detect(Path.of("/tmp"), null)).willReturn("f.nsh");

        BundleEntrypointDetector detector = new BundleEntrypointDetector(List.of(asus, gigabyte));

        String result = detector.detect(Vendor.GIGABYTE, Path.of("/tmp"), null);

        assertThat(result).isEqualTo("f.nsh");
        verify(gigabyte).detect(Path.of("/tmp"), null);
    }

    @Test
    @DisplayName("매칭 strategy 0 : EntrypointStrategyMissingException — 새 vendor enum 추가 시 누락 가드")
    void noMatchingStrategyThrows() {
        EntrypointDetectionStrategy onlyAsus = mock(EntrypointDetectionStrategy.class);
        given(onlyAsus.supports(Vendor.FUJITSU)).willReturn(false);

        BundleEntrypointDetector detector = new BundleEntrypointDetector(List.of(onlyAsus));

        assertThatThrownBy(() -> detector.detect(Vendor.FUJITSU, Path.of("/tmp"), null))
                .isInstanceOf(EntrypointStrategyMissingException.class)
                .hasMessageContaining("FUJITSU");
    }
}
