package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.common.firmware.exception.FirmwareFilePolicyMissingException;
import com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException;
import com.example.serverprovision.management.bios.firmware.AsusFirmwareFilePolicyStrategy;
import com.example.serverprovision.management.bios.firmware.FujitsuFirmwareFilePolicyStrategy;
import com.example.serverprovision.management.bios.firmware.GigabyteFirmwareFilePolicyStrategy;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R12-1 — vendor 매칭 dispatcher 단위 테스트. 정책 본문은 전략 테스트가 검증하고,
 * 여기서는 매칭 · 누락 감지 · 뷰 파생 문자열을 본다.
 */
class BiosFirmwareFilePolicyTest {

    private final BiosFirmwareFilePolicy policy = new BiosFirmwareFilePolicy(List.of(
            new GigabyteFirmwareFilePolicyStrategy(),
            new AsusFirmwareFilePolicyStrategy(),
            new FujitsuFirmwareFilePolicyStrategy()
    ));

    @Test
    @DisplayName("vendor 격리 : 같은 파일명이 GIGABYTE 에선 거절, ASUS · FUJITSU 에선 통과")
    void vendorIsolation() {
        assertThatThrownBy(() -> policy.assertAllowed(Vendor.GIGABYTE, "bios.cap", "firmwareFile"))
                .isInstanceOf(InvalidFirmwareFileException.class);
        assertThatCode(() -> policy.assertAllowed(Vendor.ASUS, "bios.cap", "firmwareFile"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertAllowed(Vendor.FUJITSU, "anything.bin", "firmwareFile"))
                .doesNotThrowAnyException();
        // 금지 파일명도 GIGABYTE 전용 — 다른 제조사에 횡전파되지 않는다.
        assertThatCode(() -> policy.assertAllowed(Vendor.ASUS, "PFR1.RBU", "firmwareFile"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("전략 누락 : 매칭 구현체 없으면 FirmwareFilePolicyMissingException (개발 규칙 가드)")
    void missingStrategy_throws() {
        BiosFirmwareFilePolicy empty = new BiosFirmwareFilePolicy(List.of());
        assertThatThrownBy(() -> empty.assertAllowed(Vendor.GIGABYTE, "image.RBU", "firmwareFile"))
                .isInstanceOf(FirmwareFilePolicyMissingException.class)
                .hasMessageContaining("GIGABYTE");
    }

    @Test
    @DisplayName("뷰 파생 : accept 속성은 소문자 · 대문자 두 표기, 제약 없는 vendor 는 전부 빈 문자열")
    void viewDerivations() {
        assertThat(policy.acceptAttribute(Vendor.GIGABYTE)).isEqualTo(".rbu,.RBU");
        assertThat(policy.allowedExtensionsCsv(Vendor.GIGABYTE)).isEqualTo("rbu");
        assertThat(policy.forbiddenNamesCsv(Vendor.GIGABYTE)).isEqualTo("PFR1.RBU,PFR2.RBU");
        assertThat(policy.invalidExtensionMessage(Vendor.GIGABYTE)).contains(".RBU");

        assertThat(policy.acceptAttribute(Vendor.ASUS)).isEmpty();
        assertThat(policy.allowedExtensionsCsv(Vendor.ASUS)).isEmpty();
        assertThat(policy.forbiddenNamesCsv(Vendor.ASUS)).isEmpty();
        assertThat(policy.forbiddenMessage(Vendor.ASUS)).isEmpty();
        assertThat(policy.invalidExtensionMessage(Vendor.FUJITSU)).isEmpty();
    }
}
