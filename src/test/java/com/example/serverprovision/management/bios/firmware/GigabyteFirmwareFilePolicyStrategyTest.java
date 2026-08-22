package com.example.serverprovision.management.bios.firmware;

import com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R12-1 — GIGABYTE 펌웨어 파일명 정책 단위 테스트.
 * 검사 알고리즘은 인터페이스 default 본문이므로 본 테스트가 알고리즘 + GIGABYTE 데이터 결합을 함께 검증한다.
 */
class GigabyteFirmwareFilePolicyStrategyTest {

    private final GigabyteFirmwareFilePolicyStrategy strategy = new GigabyteFirmwareFilePolicyStrategy();

    @Test
    @DisplayName("supports : GIGABYTE 만 담당")
    void supports_gigabyteOnly() {
        assertThat(strategy.supports(Vendor.GIGABYTE)).isTrue();
        assertThat(strategy.supports(Vendor.ASUS)).isFalse();
        assertThat(strategy.supports(Vendor.FUJITSU)).isFalse();
    }

    @Test
    @DisplayName("assertAllowed(happy) : image.RBU · 대소문자 변형 전부 통과")
    void assertAllowed_rbuVariants_pass() {
        assertThatCode(() -> strategy.assertAllowed("image.RBU", "firmwareFile")).doesNotThrowAnyException();
        assertThatCode(() -> strategy.assertAllowed("Image.rbu", "firmwareFile")).doesNotThrowAnyException();
        assertThatCode(() -> strategy.assertAllowed("IMAGE.RBU", "firmwarePath")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertAllowed(fail) : 금지 파일명 PFR1.RBU · pfr2.rbu → 400 필드 직결")
    void assertAllowed_forbiddenNames_throw() {
        assertThatThrownBy(() -> strategy.assertAllowed("PFR1.RBU", "firmwareFile"))
                .isInstanceOf(InvalidFirmwareFileException.class)
                .satisfies(e -> assertThat(((InvalidFirmwareFileException) e).fieldName()).isEqualTo("firmwareFile"))
                .hasMessageContaining("PFR");
        assertThatThrownBy(() -> strategy.assertAllowed("pfr2.rbu", "firmwarePath"))
                .isInstanceOf(InvalidFirmwareFileException.class)
                .satisfies(e -> assertThat(((InvalidFirmwareFileException) e).fieldName()).isEqualTo("firmwarePath"));
    }

    @Test
    @DisplayName("assertAllowed(fail) : 허용 확장자 밖 (.zip · .cap · 확장자 없음) → 400")
    void assertAllowed_invalidExtension_throws() {
        assertThatThrownBy(() -> strategy.assertAllowed("bundle.zip", "firmwareFile"))
                .isInstanceOf(InvalidFirmwareFileException.class)
                .hasMessageContaining(".RBU");
        assertThatThrownBy(() -> strategy.assertAllowed("bios.cap", "firmwareFile"))
                .isInstanceOf(InvalidFirmwareFileException.class);
        assertThatThrownBy(() -> strategy.assertAllowed("firmware", "firmwarePath"))
                .isInstanceOf(InvalidFirmwareFileException.class);
    }

    @Test
    @DisplayName("assertAllowed : null · blank 는 검사 대상 없음으로 통과 (필수 여부는 호출측 소관)")
    void assertAllowed_nullOrBlank_pass() {
        assertThatCode(() -> strategy.assertAllowed(null, "firmwareFile")).doesNotThrowAnyException();
        assertThatCode(() -> strategy.assertAllowed("  ", "firmwareFile")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("검사 순서 : 금지 파일명이 확장자보다 먼저 — PFR1.RBU 는 확장자가 유효해도 금지 사유로 거절")
    void forbiddenName_checkedBeforeExtension() {
        assertThatThrownBy(() -> strategy.assertAllowed("PFR1.RBU", "firmwareFile"))
                .hasMessageContaining("PFR 사본");
    }

    @Test
    @DisplayName("뷰 데이터 : 금지 CSV · 문구 정본 · 허용 확장자")
    void viewData() {
        assertThat(strategy.forbiddenNamesCsv()).isEqualTo("PFR1.RBU,PFR2.RBU");
        assertThat(strategy.forbiddenMessage()).contains("PFR 사본");
        assertThat(strategy.allowedExtensions()).containsExactly("rbu");
        assertThat(strategy.invalidExtensionMessage()).contains(".RBU");
    }
}
