package com.example.serverprovision.management.raidcard.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MA7 — {@link RaidLevel#minimumDisks(boolean)} 판정 단위 테스트 (CP6 사용자 교정 회귀 가드).
 *
 * <p>최소 디스크 수는 레벨 단독의 성질이 아니다 — 캐시 없는 카드의 RAID0 은 2개 강제이지만
 * 캐시 보유 카드는 단일 디스크 RAID0 구성이 실무에 존재한다. RAID0 만 캐시에 반응하고
 * 나머지 레벨은 캐시와 무관해야 한다.</p>
 */
class RaidLevelTest {

	@Test
	@DisplayName("RAID0 — 캐시 없는 카드는 2개 강제, 캐시 보유 카드는 단일 디스크 허용")
	void raid0_minimumDependsOnCache() {
		assertThat(RaidLevel.RAID0.minimumDisks(false)).isEqualTo(2);
		assertThat(RaidLevel.RAID0.minimumDisks(true)).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({"RAID1,2", "RAID5,3", "RAID6,4", "RAID10,4"})
	@DisplayName("RAID0 외 레벨 — 캐시 유무와 무관하게 일반 최소치 유지")
	void otherLevels_ignoreCache(RaidLevel level, int expected) {
		assertThat(level.minimumDisks(false)).isEqualTo(expected);
		assertThat(level.minimumDisks(true)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"RAID0,2,2", "RAID0,5,5", "RAID1,2,1", "RAID1,4,1", "RAID5,3,2", "RAID5,6,5", "RAID6,4,2", "RAID6,5,3", "RAID10,4,2", "RAID10,8,4"})
	@DisplayName("usableDisks — RAID0 n · RAID1 1 · RAID5 n−1 · RAID6 n−2 · RAID10 n/2 (U4-1-3 D7, 하한 계산 재료)")
	void usableDisks_perLevel(RaidLevel level, int members, int expected) {
		assertThat(level.usableDisks(members)).isEqualTo(expected);
	}

	@Test
	@DisplayName("usableDisks 는 n 에 단조 증가(RAID1 은 상수) — 'n 개 이상' 에 최소 n 을 넣은 값이 하한이라는 근거")
	void usableDisks_monotonic() {
		for (RaidLevel level : RaidLevel.values()) {
			for (int n = level.minimumDisks(false); n < 12; n++) {
				assertThat(level.usableDisks(n + 1)).isGreaterThanOrEqualTo(level.usableDisks(n));
			}
		}
	}
}
