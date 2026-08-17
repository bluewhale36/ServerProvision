package com.example.serverprovision.management.raidcard.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RAID 카드 제조사. MA7 MVP 는 실제 보유 장비 기준 2종.
 * 새 제조사를 지원하려면 이 열거형에 상수를 추가한다 — 런타임 등록은 지원하지 않는다.
 *
 * <p>메인보드의 {@code management/board/enums/Vendor} 를 재사용하지 않는다(MA7 D5) — 그 enum 은
 * PXE 부팅 보고 문자열 정규화(ipxeName / canonicalizeReportedModel)라는 보드 전용 책임을 지녀,
 * PXE 가 보고하지 않는 RAID 카드가 상속하면 의미 없는 필드만 물려받는다. 같은 회사(GIGABYTE)가
 * 두 enum 에 나타나는 것은 중복이 아니라 자원 도메인별 책임 분리다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum RaidCardVendor {

	GIGABYTE("GIGABYTE"),
	AVAGO("AVAGO");

	private final String displayName;
}
