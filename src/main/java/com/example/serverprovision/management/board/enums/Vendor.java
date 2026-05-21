package com.example.serverprovision.management.board.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 메인보드 제조사. A2 MVP 는 실제 보유 장비 기준 3종.
 * 새 제조사를 지원하려면 이 열거형에 상수를 추가한다 — 런타임 등록은 지원하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum Vendor {

	GIGABYTE("Gigabyte"),
	ASUS("Asus"),
	FUJITSU("Fujitsu");

	private final String displayName;
}
