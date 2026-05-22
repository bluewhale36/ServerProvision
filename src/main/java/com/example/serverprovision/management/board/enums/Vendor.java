package com.example.serverprovision.management.board.enums;

import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.VendorNotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 메인보드 제조사. A2 MVP 는 실제 보유 장비 기준 3종.
 * 새 제조사를 지원하려면 이 열거형에 상수를 추가한다 — 런타임 등록은 지원하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum Vendor {

	GIGABYTE("Gigabyte", "Giga Computing"),
	ASUS("Asus", "Asus"),
	FUJITSU("Fujitsu", "FUJITSU");

	private final String displayName;
	private final String ipxeName;

	public static Vendor findByIpxeName(String ipxeNameStr) {
		return Arrays.stream(Vendor.values())
				.filter(v -> v.ipxeName.equals(ipxeNameStr))
				.findFirst()
				.orElseThrow(
						() -> new VendorNotFoundException(ipxeNameStr)
				);
	}
}
