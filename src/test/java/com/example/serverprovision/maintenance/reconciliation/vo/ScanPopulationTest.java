package com.example.serverprovision.maintenance.reconciliation.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MK4-4-2 — 점검이 본 세 모집단.
 *
 * <p>이 값 객체가 존재하는 이유는 화면의 모순을 없애는 데 있다. 종전에는 활성 자원만 세면서
 * 삭제 자원 · 짝 없는 마커에서 나온 문제를 목록에 실어, 탐지 건수가 점검 대상 수를 넘는 화면이
 * 만들어졌다. 그래서 이 테스트가 가장 먼저 확인하는 것은 <b>총계가 셋의 합</b>이라는 사실이다.</p>
 */
class ScanPopulationTest {

	@Nested
	@DisplayName("총계")
	class Total {

		@Test
		@DisplayName("셋의 합이다 — 활성만 세던 종전 값과 달라지는 지점")
		void 총계는_셋의_합() {
			ScanPopulation population = ScanPopulation.of(9, 6, 1);

			assertThat(population.total()).isEqualTo(16);
			assertThat(population.getActiveCount()).isEqualTo(9);
		}

		@Test
		@DisplayName("셋 다 0 이면 0")
		void 아무것도_보지_않았으면_0() {
			assertThat(ScanPopulation.EMPTY.total()).isZero();
		}
	}

	@Nested
	@DisplayName("활성 밖 모집단 유무")
	class HasNonActive {

		@Test
		@DisplayName("활성만 봤으면 내역을 펼칠 이유가 없다")
		void 활성만_봤으면_false() {
			assertThat(ScanPopulation.of(9, 0, 0).hasNonActive()).isFalse();
		}

		@Test
		@DisplayName("삭제 자원을 하나라도 봤으면 내역이 필요하다")
		void 삭제를_봤으면_true() {
			assertThat(ScanPopulation.of(9, 1, 0).hasNonActive()).isTrue();
		}

		@Test
		@DisplayName("짝 없는 마커를 하나라도 봤으면 내역이 필요하다")
		void 짝없는_마커를_봤으면_true() {
			assertThat(ScanPopulation.of(9, 0, 1).hasNonActive()).isTrue();
		}
	}

	@Nested
	@DisplayName("불변식")
	class Invariant {

		/**
		 * 정상 흐름으로는 도달하지 않는다 — 세는 쪽이 컬렉션 크기를 넘기기 때문이다. 그래도 가드를
		 * 두는 이유는 음수가 저장되면 총계가 조용히 줄어 화면이 다시 거짓을 말하게 되기 때문이다.
		 */
		@Test
		@DisplayName("음수는 거절한다")
		void 음수_거절() {
			assertThatThrownBy(() -> ScanPopulation.of(-1, 0, 0))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("음수");
			assertThatThrownBy(() -> ScanPopulation.of(0, -1, 0))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> ScanPopulation.of(0, 0, -1))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
