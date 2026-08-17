package com.example.serverprovision.management.raidcard.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RAID 카드가 만들 수 있는 RAID 레벨.
 *
 * <p>레벨은 순서가 아니다 — RAID10 을 지원하면서 RAID6 은 못 만드는 카드가 있어 "최대 레벨" 하나로
 * 표현할 수 없고, 지원 집합({@code SupportedRaidLevels})으로 표현한다(MA7 D6).</p>
 *
 * <p><b>최소 디스크 수는 레벨 단독의 성질이 아니다(CP6 사용자 교정).</b> 캐시가 없는 카드는 RAID0 에
 * 디스크 2개 이상이 강제되지만, 캐시 보유 카드는 <b>단일 디스크를 RAID0 으로 구성하는 실무가 있다</b>
 * (write-back 캐시 이점을 단일 디스크에 씌우는 구성). 그래서 판정은 {@link #minimumDisks(boolean)} 로
 * 카드의 캐시 유무를 받고, 캐시가 규칙을 바꾸는 상수(RAID0)만 method-per-constant 로 override 한다
 * ({@code Vendor.canonicalizeReportedModel} 선례). U4-1-1 의 정의서 작성 폼이 디스크 개수 판정에 쓴다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum RaidLevel {

	RAID0("RAID0", 2, "을", false) {
		/** 캐시 보유 카드는 단일 디스크 RAID0 구성이 실무에 존재한다 — 캐시가 최소치를 1로 낮춘다. */
		@Override
		public int minimumDisks(boolean cardHasCache) {
			return cardHasCache ? 1 : getBaseMinimumDisks();
		}
	},
	RAID1("RAID1", 2, "을", false),
	RAID5("RAID5", 3, "를", true),
	RAID6("RAID6", 4, "을", true),
	RAID10("RAID10", 4, "을", true);

	private final String displayName;

	/** 캐시 없는 카드 기준의 일반 최소 디스크 수. 캐시가 규칙을 바꾸는 레벨은 {@link #minimumDisks(boolean)} 를 override. */
	private final int baseMinimumDisks;

	/**
	 * 차단 사유 문장 조립용 목적격 조사. 레벨명이 숫자로 끝나 읽기에 따라 받침이 갈리므로
	 * (0=영 → 을, 5=오 → 를) 상수가 직접 보유한다 — 병기형('을(를)') 표기를 피하기 위함(HF5 선례).
	 */
	private final String objectParticle;

	/**
	 * 이 레벨을 지원하는 컨트롤러가 일반적으로 캐시를 동반하는가(CP6 개정). 업무 관례 "같은 스펙 디스크
	 * 3개 이상이면 캐시 있는 카드" 의 실제 이유가 이것이다 — RAID5 이상을 만들 수 있는 컨트롤러가 대개
	 * 캐시를 갖는다. 등록 · 수정 폼이 캐시 0 + 이 레벨 선택 조합에 <b>경고 모달</b>을 띄우는 근거이며,
	 * 차단이 아니라 확인 후 진행을 허용한다(실물이 실제로 그런 사양일 수 있으므로 서버 가드는 두지 않는다).
	 */
	private final boolean typicallyRequiresCache;

	/**
	 * 이 레벨을 구성하는 데 필요한 최소 디스크 수 — 카드의 캐시 유무가 규칙에 관여한다.
	 * 기본은 일반 최소치 그대로이고, 캐시가 예외를 만드는 상수만 override 한다.
	 */
	public int minimumDisks(boolean cardHasCache) {
		return baseMinimumDisks;
	}
}
