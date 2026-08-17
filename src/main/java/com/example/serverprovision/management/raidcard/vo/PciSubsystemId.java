package com.example.serverprovision.management.raidcard.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PCI Subsystem ID — 완제품 카드를 식별하는 (Subsystem Vendor ID, Subsystem Device ID) 쌍.
 *
 * <p>Device ID 단독으로는 어떤 카드도 가리키지 못하고 Vendor 와 쌍일 때만 카드 하나를 식별하므로
 * 두 값을 하나의 VO 로 감싼다(MA7 D4) — 반쪽만 채워진 상태가 타입 차원에서 차단되고, '미확인' 은
 * 임베디드 전체가 null 인 상태로 자연히 표현된다. {@code lspci -nn} 의 출력도 {@code [1458:0011]}
 * 형태의 쌍이다.</p>
 *
 * <p>표기 차이({@code 1458:0011} / {@code 0x1458:0x0011} / 대괄호 포함)를 {@link #parse(String)} 가
 * 흡수하고 {@link #toDisplay()} 가 소문자 4자리 16진수 쌍으로 정규화한다 — 같은 카드가 표기 차이로
 * 다른 값처럼 비교되는 사고를 타입에서 막는다({@code MacAddressVO} 와 같은 이유).</p>
 */
@Embeddable
public record PciSubsystemId(

		@Column(name = "pci_subsystem_vendor_id")
		Integer vendorId,

		@Column(name = "pci_subsystem_device_id")
		Integer deviceId
) {

	private static final int MAX_16BIT = 0xFFFF;

	// "1458:0011" / "0x1458:0x0011" / "[1458:0011]" 를 모두 허용. 내용은 항상 16진수 1~4자리 쌍.
	private static final Pattern PAIR_PATTERN = Pattern.compile(
			"^\\[?(?:0[xX])?([0-9a-fA-F]{1,4}):(?:0[xX])?([0-9a-fA-F]{1,4})]?$");

	public PciSubsystemId {
		if (vendorId == null || deviceId == null) {
			throw new IllegalArgumentException("PCI Subsystem ID 는 Vendor / Device 두 값이 모두 필요합니다.");
		}
		if (vendorId < 0 || vendorId > MAX_16BIT || deviceId < 0 || deviceId > MAX_16BIT) {
			throw new IllegalArgumentException(
					"PCI Subsystem ID 는 16비트 범위(0x0000~0xFFFF)여야 합니다 : " + vendorId + ", " + deviceId);
		}
	}

	/**
	 * 문자열 표기를 흡수해 생성한다. 빈 값은 호출자가 null 로 처리한다(선택 입력 — '미확인').
	 */
	public static PciSubsystemId parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("PCI Subsystem ID 가 빈 값입니다. 미확인은 null 로 표현하세요.");
		}
		Matcher m = PAIR_PATTERN.matcher(raw.trim());
		if (!m.matches()) {
			throw new IllegalArgumentException("PCI Subsystem ID 형식이 올바르지 않습니다 : " + raw);
		}
		return new PciSubsystemId(Integer.parseInt(m.group(1), 16), Integer.parseInt(m.group(2), 16));
	}

	/** 소문자 4자리 16진수 쌍 정규 표기 (예: {@code 1458:0011}). */
	public String toDisplay() {
		return "%04x:%04x".formatted(vendorId, deviceId);
	}
}
