package com.example.serverprovision.management.raidcard.vo;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MA7 D6 — {@link SupportedRaidLevels} 판정 SSOT 단위 테스트.
 *
 * <p>{@code blockReasonFor} 의 반환 규약(null = 통과, 문자열 = 화면 안내이자 거절 사유)이
 * U4-1-1 과의 계약이므로 통과 · 차단 양쪽의 정확한 형태를 고정한다.</p>
 */
class SupportedRaidLevelsTest {

	// ==== 판정 =======================================================

	@Test
	@DisplayName("blockReasonFor — 지원하는 레벨 → null (통과)")
	void blockReasonFor_supportedLevel_returnsNull() {
		SupportedRaidLevels levels = SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1));

		assertThat(levels.blockReasonFor(RaidLevel.RAID1)).isNull();
		assertThat(levels.supports(RaidLevel.RAID1)).isTrue();
	}

	@Test
	@DisplayName("blockReasonFor — 못 만드는 레벨 → 사유 문장 (요구 레벨 + 지원 목록 포함)")
	void blockReasonFor_unsupportedLevel_returnsReason() {
		// CRA3338 실측 사양 — RAID0 · RAID1 만 (브리핑 §3-1).
		SupportedRaidLevels cra3338 = SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1));

		String reason = cra3338.blockReasonFor(RaidLevel.RAID5);

		assertThat(reason)
				.contains("RAID5")
				.contains("만들 수 없는")
				.contains("RAID0 · RAID1");
		assertThat(cra3338.supports(RaidLevel.RAID5)).isFalse();
	}

	@Test
	@DisplayName("레벨은 순서가 아니다 — RAID10 지원 · RAID6 미지원 조합이 성립 (D6 탈락안 maxLevel 반례)")
	void levelsAreNotOrdered() {
		SupportedRaidLevels levels = SupportedRaidLevels.of(
				List.of(RaidLevel.RAID0, RaidLevel.RAID1, RaidLevel.RAID10));

		assertThat(levels.supports(RaidLevel.RAID10)).isTrue();
		assertThat(levels.supports(RaidLevel.RAID6)).isFalse();
	}

	// ==== 표시 · 정렬 =================================================

	@Test
	@DisplayName("toDisplay — 입력 순서와 무관하게 선언 순 정렬 + ' · ' join")
	void toDisplay_sortsByDeclarationOrder() {
		SupportedRaidLevels levels = SupportedRaidLevels.of(
				List.of(RaidLevel.RAID5, RaidLevel.RAID0, RaidLevel.RAID1));

		assertThat(levels.toDisplay()).isEqualTo("RAID0 · RAID1 · RAID5");
	}

	// ==== invariant ==================================================

	@Test
	@DisplayName("빈 집합 거절 — 아무 레벨도 못 만드는 RAID 카드는 도메인 모순")
	void rejectsEmptySet() {
		assertThatThrownBy(() -> SupportedRaidLevels.of(List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("최소 1개");
		assertThatThrownBy(() -> SupportedRaidLevels.of(null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("asSet — 불변 (수정 시도는 거절)")
	void asSet_isImmutable() {
		Set<RaidLevel> set = SupportedRaidLevels.of(List.of(RaidLevel.RAID0)).asSet();

		assertThatThrownBy(() -> set.add(RaidLevel.RAID5))
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
