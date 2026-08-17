package com.example.serverprovision.management.raidcard.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MA7 D4 — {@link PciSubsystemId} 표기 흡수 · 정규화 · 범위 검증 단위 테스트.
 *
 * <p>같은 카드가 표기 차이(콜론 쌍 / 0x 접두 / lspci 대괄호)로 다른 값처럼 비교되는 사고를 타입이
 * 막는다는 것이 이 VO 의 존재 이유 — 세 표기가 같은 값으로 수렴하는지가 핵심 검증이다.</p>
 */
class PciSubsystemIdTest {

	// ==== parse — 표기 흡수 ===========================================

	@ParameterizedTest
	@ValueSource(strings = {"1458:0011", "0x1458:0x0011", "[1458:0011]", " 1458:0011 ", "1458:11"})
	@DisplayName("parse — 콜론 쌍 · 0x 접두 · 대괄호(lspci -nn) · 공백 · 축약 표기가 모두 같은 값으로 수렴")
	void parse_absorbsNotationVariants(String raw) {
		PciSubsystemId parsed = PciSubsystemId.parse(raw);

		assertThat(parsed.vendorId()).isEqualTo(0x1458);
		assertThat(parsed.deviceId()).isEqualTo(0x0011);
		assertThat(parsed.toDisplay()).isEqualTo("1458:0011");
	}

	@Test
	@DisplayName("toDisplay — 소문자 4자리 16진수 쌍으로 정규화 (대문자 입력도 동일 출력)")
	void toDisplay_normalizesToLowercase4Digits() {
		assertThat(PciSubsystemId.parse("0xABCD:0xEF01").toDisplay()).isEqualTo("abcd:ef01");
	}

	// ==== parse — 거절 ================================================

	@ParameterizedTest
	@ValueSource(strings = {"zzzz:0011", "1458", "1458:0011:0022", "14580011", "0x14580:0x11"})
	@DisplayName("parse — 16진수 아님 · 반쪽 · 3토큰 · 구분자 없음 · 5자리 → 형식 거절")
	void parse_rejectsMalformed(String raw) {
		assertThatThrownBy(() -> PciSubsystemId.parse(raw))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("형식");
	}

	@Test
	@DisplayName("parse — 빈 값은 거절 (미확인은 null 로 표현하는 계약)")
	void parse_rejectsBlank() {
		assertThatThrownBy(() -> PciSubsystemId.parse("  "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ==== 생성자 invariant ============================================

	@Test
	@DisplayName("생성자 — 반쪽 null → 거절 (Vendor/Device 쌍이어야 카드 하나를 식별)")
	void constructor_rejectsHalfNull() {
		assertThatThrownBy(() -> new PciSubsystemId(0x1458, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PciSubsystemId(null, 0x0011))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("생성자 — 16비트 범위 초과 → 거절")
	void constructor_rejectsOutOfRange() {
		assertThatThrownBy(() -> new PciSubsystemId(0x10000, 0x0011))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("16비트");
		assertThatThrownBy(() -> new PciSubsystemId(0x1458, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
