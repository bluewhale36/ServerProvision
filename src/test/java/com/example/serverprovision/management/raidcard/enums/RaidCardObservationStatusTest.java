package com.example.serverprovision.management.raidcard.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3.5-5-b D2 — 관측 상태 진리표(distinct 관측 수 × 확정값) 전수 + 상수별 파생값. 화면 · 서비스 가드가 이 표 하나를 본다.
 */
class RaidCardObservationStatusTest {

	@Test
	@DisplayName("distinct 0 — 확정값 유무와 무관하게 NONE · 버튼 없음 · 사유 있음")
	void none() {
		assertThat(RaidCardObservationStatus.of(null, Set.of())).isEqualTo(RaidCardObservationStatus.NONE);
		assertThat(RaidCardObservationStatus.of("1000:9361", Set.of())).isEqualTo(RaidCardObservationStatus.NONE);
		assertThat(RaidCardObservationStatus.NONE.confirmable()).isFalse();
		assertThat(RaidCardObservationStatus.NONE.showsButton(false)).isFalse();
		assertThat(RaidCardObservationStatus.NONE.badgeClass()).isNull();
		assertThat(RaidCardObservationStatus.NONE.blockReason()).contains("관측이 없습니다");
	}

	@Test
	@DisplayName("distinct 1 + 미확인 — AGREED_UNCONFIRMED 가 유일한 confirmable · 사유 null")
	void agreedUnconfirmed() {
		RaidCardObservationStatus status = RaidCardObservationStatus.of(null, Set.of("1000:9361"));
		assertThat(status).isEqualTo(RaidCardObservationStatus.AGREED_UNCONFIRMED);
		assertThat(status.confirmable()).isTrue();
		assertThat(status.showsButton(false)).isTrue();
		assertThat(status.badgeClass()).isEqualTo("n-badge-blue");
		assertThat(status.blockReason()).isNull();
	}

	@Test
	@DisplayName("distinct 1 + 확정값 같음(대소문자 무시) — MATCHES_CONFIRMED · 버튼 없음")
	void matchesConfirmed() {
		assertThat(RaidCardObservationStatus.of("1000:9361", Set.of("1000:9361"))).isEqualTo(RaidCardObservationStatus.MATCHES_CONFIRMED);
		assertThat(RaidCardObservationStatus.of("1000:9361", Set.of("1000:9361".toUpperCase()))).isEqualTo(RaidCardObservationStatus.MATCHES_CONFIRMED);
		assertThat(RaidCardObservationStatus.MATCHES_CONFIRMED.confirmable()).isFalse();
		assertThat(RaidCardObservationStatus.MATCHES_CONFIRMED.showsButton(true)).isFalse();
		assertThat(RaidCardObservationStatus.MATCHES_CONFIRMED.blockReason()).contains("이미 확정된");
	}

	@Test
	@DisplayName("distinct 1 + 확정값 다름 — DIFFERS_FROM_CONFIRMED · 관측으로 덮어쓰지 않는다")
	void differsFromConfirmed() {
		RaidCardObservationStatus status = RaidCardObservationStatus.of("1000:9361", Set.of("1000:00ce"));
		assertThat(status).isEqualTo(RaidCardObservationStatus.DIFFERS_FROM_CONFIRMED);
		assertThat(status.confirmable()).isFalse();
		assertThat(status.showsButton(true)).isFalse();
		assertThat(status.badgeClass()).isEqualTo("n-badge-red");
	}

	@Test
	@DisplayName("distinct 2 이상 — 확정값 유무와 무관하게 CONFLICTING · 버튼은 그리되 잠근다")
	void conflicting() {
		assertThat(RaidCardObservationStatus.of(null, Set.of("1000:9361", "1000:00ce"))).isEqualTo(RaidCardObservationStatus.CONFLICTING);
		assertThat(RaidCardObservationStatus.of("1000:9361", Set.of("1000:9361", "1000:00ce"))).isEqualTo(RaidCardObservationStatus.CONFLICTING);
		assertThat(RaidCardObservationStatus.CONFLICTING.confirmable()).isFalse();
		assertThat(RaidCardObservationStatus.CONFLICTING.showsButton(false)).as("미확인 카드 — 잠긴 버튼 + tooltip").isTrue();
		assertThat(RaidCardObservationStatus.CONFLICTING.showsButton(true)).as("확정된 카드 — 확정 행위가 성립하지 않으므로 숨김(F-2)").isFalse();
		assertThat(RaidCardObservationStatus.CONFLICTING.blockReason()).contains("서로 다릅니다");
	}
}
