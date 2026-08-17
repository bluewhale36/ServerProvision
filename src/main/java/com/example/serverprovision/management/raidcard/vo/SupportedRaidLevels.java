package com.example.serverprovision.management.raidcard.vo;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * RAID 카드가 지원하는 RAID 레벨 집합 — "이 카드로 이 레벨을 만들 수 있는가" 판정의 SSOT
 * (Single Source of Truth, MA7 D6).
 *
 * <p>맨손 {@code Set<RaidLevel>} 로 두면 판정이 호출부마다 {@code contains} 로 흩어진다. VO 가
 * {@link #supports}/{@link #blockReasonFor} 를 보유하면 U4-1-1 의 정의서 작성 폼 disabled 플래그와
 * 서버 가드가 같은 메서드 하나를 호출한다 — 반환 규약은 {@code RequiredBoardModel.blockReasonFor}
 * 에서 가져왔다(null = 통과, 아니면 그 문자열이 화면 안내이자 거절 사유). 단 그 선례는 할당 시점
 * (정의서 ↔ 서버) 판정이고 본 VO 는 작성 시점(카드 능력 ↔ 요구 레벨) 판정이라, 같은 것은 반환
 * 규약뿐이다.</p>
 *
 * <p>불변 · {@link RaidLevel} 선언 순 정렬 집합. 빈 집합은 거절한다 — 지원 레벨은 등록 필수
 * 입력이며(D2), "아무 레벨도 못 만드는 RAID 카드" 는 도메인 모순이다.</p>
 */
public record SupportedRaidLevels(SortedSet<RaidLevel> levels) {

	public SupportedRaidLevels {
		if (levels == null || levels.isEmpty()) {
			throw new IllegalArgumentException("지원 RAID 레벨은 최소 1개 이상이어야 합니다.");
		}
		levels = Collections.unmodifiableSortedSet(new TreeSet<>(levels));
	}

	public static SupportedRaidLevels of(Collection<RaidLevel> levels) {
		if (levels == null || levels.isEmpty()) {
			throw new IllegalArgumentException("지원 RAID 레벨은 최소 1개 이상이어야 합니다.");
		}
		return new SupportedRaidLevels(new TreeSet<>(levels));
	}

	public boolean supports(RaidLevel level) {
		return levels.contains(level);
	}

	/**
	 * 요구 레벨을 이 카드로 만들 수 있는지 판정한다. {@code null} = 통과, 아니면 반환 문자열이
	 * 화면 안내(disabled tooltip)이자 서버 거절 사유가 된다 — 두 곳이 이 메서드 하나를 공유한다.
	 */
	public String blockReasonFor(RaidLevel wanted) {
		if (supports(wanted)) {
			return null;
		}
		return wanted.getDisplayName() + " " + wanted.getObjectParticle()
				+ " 만들 수 없는 카드입니다 — 지원하는 RAID 레벨은 " + toDisplay() + " 입니다.";
	}

	/** 화면 표시용 — {@code RAID0 · RAID1} 형태(선언 순). */
	public String toDisplay() {
		return levels.stream().map(RaidLevel::getDisplayName).collect(Collectors.joining(" · "));
	}

	/** 불변 정렬 집합(선언 순). 조회 응답이 그대로 실어 나른다. */
	public Set<RaidLevel> asSet() {
		return levels;
	}
}
