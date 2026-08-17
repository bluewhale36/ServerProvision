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
}
