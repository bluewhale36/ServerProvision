package com.example.serverprovision.management.raidcard.vo;

/**
 * RAID 카드 온보드 캐시 용량(GB). {@code 0 = 캐시 없음}.
 *
 * <p>CP6 개정 — 캐시를 유무(boolean)가 아니라 용량으로 모델링한다. 운영자가 카드를 식별하는
 * 표기가 "AVAGO 9361-8i, 2GB" 처럼 용량이고, 캐시 존재 여부는 {@link #isPresent()} 로 파생된다 —
 * 그 파생값이 {@code RaidLevel.minimumDisks(cardHasCache)} 판정의 입력이다(캐시 보유 카드는
 * 단일 디스크 RAID0 구성이 실무에 존재).</p>
 */
public record CacheCapacity(int gigabytes) {

	/** 상한은 온보드 캐시의 현실적 한계 — 오타(예: 2048 을 GB 로 오인) fail-fast 용. */
	private static final int MAX_GIGABYTES = 1024;

	public static final CacheCapacity NONE = new CacheCapacity(0);

	public CacheCapacity {
		if (gigabytes < 0 || gigabytes > MAX_GIGABYTES) {
			throw new IllegalArgumentException(
					"캐시 용량은 0~" + MAX_GIGABYTES + "GB 범위여야 합니다 : " + gigabytes);
		}
	}

	public static CacheCapacity ofGigabytes(int gigabytes) {
		return gigabytes == 0 ? NONE : new CacheCapacity(gigabytes);
	}

	/** 캐시 보유 여부 — {@code RaidLevel.minimumDisks} 판정의 입력. */
	public boolean isPresent() {
		return gigabytes > 0;
	}

	/** 화면 표시용 — {@code 없음} 또는 {@code 2GB}. */
	public String toDisplay() {
		return isPresent() ? gigabytes + "GB" : "없음";
	}
}
