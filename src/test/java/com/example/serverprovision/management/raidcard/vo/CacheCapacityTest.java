package com.example.serverprovision.management.raidcard.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MA7 CP6 개정 — {@link CacheCapacity} 단위 테스트. 0 = 캐시 없음, 파생 {@code isPresent()} 가
 * RAID0 최소 디스크 판정({@code RaidLevel.minimumDisks})의 입력이다.
 */
class CacheCapacityTest {

	@Test
	@DisplayName("0 = 캐시 없음 — isPresent false · 표시 '없음' · NONE 상수와 동등")
	void zero_meansNone() {
		CacheCapacity none = CacheCapacity.ofGigabytes(0);

		assertThat(none.isPresent()).isFalse();
		assertThat(none.toDisplay()).isEqualTo("없음");
		assertThat(none).isEqualTo(CacheCapacity.NONE);
	}

	@Test
	@DisplayName("양수 용량 — isPresent true · 'NGB' 표시")
	void positive_displaysGigabytes() {
		CacheCapacity two = CacheCapacity.ofGigabytes(2);

		assertThat(two.isPresent()).isTrue();
		assertThat(two.toDisplay()).isEqualTo("2GB");
	}

	@Test
	@DisplayName("음수 · 상한 초과 → 거절")
	void outOfRange_rejected() {
		assertThatThrownBy(() -> CacheCapacity.ofGigabytes(-1))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CacheCapacity.ofGigabytes(1025))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("1024");
	}
}
