package com.example.serverprovision.maintenance.reconciliation.vo;

import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MK4-3-2 — "지금이 점검할 때인가" 의 판정.
 *
 * <p>이 판정을 스케줄러에서 꺼내 값으로 만든 이유가 이 파일이다. 순수 함수라 시각을 인자로 넣어
 * 진리표를 그대로 덮을 수 있다 — 발동 시각이 Spring 스케줄러 안에 있으면 이런 검증을 할 수 없고,
 * 그래서 종전 구조의 결함(겹치면 정밀이 버려짐 · 재기동하면 처음부터 다시 셈)이 오래 드러나지 않았다.</p>
 */
class ScanScheduleTest {

	private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

	private static ScanSchedule schedule(Instant lastAny, Instant lastDeep) {
		return new ScanSchedule(
				new ScanSchedule.DepthState(ScanInterval.ofMinutes(60), lastAny, null),
				new ScanSchedule.DepthState(ScanInterval.ofMinutes(1440), lastDeep, null));
	}

	@Nested
	@DisplayName("만기 판정 진리표")
	class DueDepthTruthTable {

		@Test
		@DisplayName("정밀 · 일반 모두 만기 → 정밀 하나만. 정밀이 일반을 덮는다")
		void bothDue_runsDeepOnly() {
			ScanSchedule schedule = schedule(
					NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofDays(2)));

			assertThat(schedule.dueDepth(NOW)).contains(ScanDepth.DEEP);
		}

		@Test
		@DisplayName("정밀만 만기 → 정밀")
		void onlyDeepDue_runsDeep() {
			ScanSchedule schedule = schedule(
					NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofDays(2)));

			assertThat(schedule.dueDepth(NOW)).contains(ScanDepth.DEEP);
		}

		@Test
		@DisplayName("일반만 만기 → 일반")
		void onlyQuickDue_runsQuick() {
			ScanSchedule schedule = schedule(
					NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)));

			assertThat(schedule.dueDepth(NOW)).contains(ScanDepth.QUICK);
		}

		@Test
		@DisplayName("둘 다 아직 → 아무것도 하지 않는다")
		void neitherDue_runsNothing() {
			ScanSchedule schedule = schedule(
					NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofHours(2)));

			assertThat(schedule.dueDepth(NOW)).isEmpty();
		}
	}

	@Nested
	@DisplayName("기준 시각")
	class Baseline {

		@Test
		@DisplayName("기록이 아예 없으면 정밀부터 — 한 번도 안 봤으면 지금 본다")
		void noHistory_runsDeep() {
			assertThat(schedule(null, null).dueDepth(NOW)).contains(ScanDepth.DEEP);
		}

		/**
		 * 종전 구조가 재기동마다 주기를 처음부터 다시 세던 것과 대비되는 지점이다. 기준 시각을
		 * 보고서에서 읽으므로 애플리케이션이 방금 떴어도 밀린 사실이 그대로 보인다.
		 */
		@Test
		@DisplayName("재기동해도 밀린 정밀 점검은 밀린 채로 판정된다")
		void overdueSurvivesRestart() {
			ScanSchedule schedule = schedule(
					NOW.minus(Duration.ofMinutes(1)), NOW.minus(Duration.ofDays(30)));

			assertThat(schedule.dueDepth(NOW)).contains(ScanDepth.DEEP);
		}

		/**
		 * 일반의 기준은 정밀을 포함한 마지막 점검이다. 수동으로 방금 돌렸으면 주기 점검이 곧바로
		 * 뒤따르지 않는다 — 주기의 뜻이 "최소 이 간격마다 한 번은 본다" 이기 때문이다.
		 */
		@Test
		@DisplayName("방금 수동으로 돌렸으면 일반은 만기가 아니다")
		void recentManualScanDelaysQuick() {
			ScanSchedule schedule = schedule(NOW.minus(Duration.ofMinutes(1)), NOW);

			assertThat(schedule.dueDepth(NOW)).isEmpty();
		}

		@Test
		@DisplayName("정밀 점검은 일반의 시계도 되돌린다 — 반대는 아니다")
		void deepResetsQuickClockButNotViceVersa() {
			// 정밀을 방금 돌린 직후 : 둘 다 만기 아님
			assertThat(schedule(NOW, NOW).dueDepth(NOW)).isEmpty();
			// 일반만 방금 돌린 상태에서 정밀이 오래됐으면 : 정밀은 여전히 만기
			assertThat(schedule(NOW, NOW.minus(Duration.ofDays(2))).dueDepth(NOW))
					.contains(ScanDepth.DEEP);
		}
	}

	@Nested
	@DisplayName("주기 값")
	class Interval {

		@Test
		@DisplayName("정확히 주기만큼 지난 순간도 만기다 — 경계를 포함한다")
		void boundaryIsDue() {
			ScanInterval interval = ScanInterval.ofMinutes(60);

			assertThat(interval.isDue(NOW.minus(Duration.ofMinutes(60)), NOW)).isTrue();
			assertThat(interval.isDue(NOW.minus(Duration.ofSeconds(3599)), NOW)).isFalse();
		}

		@Test
		@DisplayName("다음 예정 시각은 마지막 점검 + 주기. 기준이 없으면 비어 있다")
		void nextDueAt() {
			ScanInterval interval = ScanInterval.ofMinutes(90);

			assertThat(interval.nextDueAt(NOW)).contains(NOW.plus(Duration.ofMinutes(90)));
			assertThat(interval.nextDueAt(null)).isEmpty();
		}

		@Test
		@DisplayName("범위를 벗어난 값은 만들 수 없다 — 값 자체가 불변식을 지킨다")
		void rejectsOutOfRange() {
			assertThatThrownBy(() -> ScanInterval.ofMinutes(0))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> ScanInterval.ofMinutes(ScanInterval.MAX_MINUTES + 1))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("사람이 읽는 표기 — 분과 함께 시간 · 일을 덧붙인다")
		void display() {
			assertThat(ScanInterval.ofMinutes(60).display()).isEqualTo("60분 (1시간)");
			assertThat(ScanInterval.ofMinutes(1440).display()).isEqualTo("1440분 (1일)");
			assertThat(ScanInterval.ofMinutes(45).display()).isEqualTo("45분");
		}

		@Test
		@DisplayName("지난 소요 시간보다 짧은지 — 경고의 판정 근거")
		void shorterThanLastRun() {
			assertThat(ScanInterval.ofMinutes(1).shorterThan(Duration.ofSeconds(252))).isTrue();
			assertThat(ScanInterval.ofMinutes(60).shorterThan(Duration.ofSeconds(252))).isFalse();
			// 기록이 없으면 비교할 대상이 없으므로 경고하지 않는다.
			assertThat(ScanInterval.ofMinutes(1).shorterThan(null)).isFalse();
			assertThat(ScanInterval.ofMinutes(1).shorterThan(Duration.ZERO)).isFalse();
		}
	}

	@Nested
	@DisplayName("깊이 관계")
	class Depth {

		@Test
		@DisplayName("정밀은 일반을 덮고 일반은 정밀을 덮지 못한다")
		void coverage() {
			assertThat(ScanDepth.DEEP.covers(ScanDepth.QUICK)).isTrue();
			assertThat(ScanDepth.DEEP.covers(ScanDepth.DEEP)).isTrue();
			assertThat(ScanDepth.QUICK.covers(ScanDepth.QUICK)).isTrue();
			assertThat(ScanDepth.QUICK.covers(ScanDepth.DEEP)).isFalse();
		}

		@Test
		@DisplayName("boolean 경계 변환")
		void booleanBoundary() {
			assertThat(ScanDepth.of(true)).isEqualTo(ScanDepth.DEEP);
			assertThat(ScanDepth.of(false)).isEqualTo(ScanDepth.QUICK);
			assertThat(ScanDepth.DEEP.isDeep()).isTrue();
			assertThat(ScanDepth.QUICK.isDeep()).isFalse();
		}

		@Test
		@DisplayName("일정에서 깊이로 상태를 되찾는다")
		void ofDepth() {
			ScanSchedule schedule = schedule(NOW, NOW);

			assertThat(schedule.of(ScanDepth.QUICK)).isSameAs(schedule.quick());
			assertThat(schedule.of(ScanDepth.DEEP)).isSameAs(schedule.deep());
		}
	}

	@Test
	@DisplayName("만기가 아니면 Optional 이 비어 있다 — '할 일 없음' 을 세 번째 상수로 만들지 않는다")
	void emptyMeansNothingToDo() {
		Optional<ScanDepth> due = schedule(NOW, NOW).dueDepth(NOW);

		assertThat(due).isEmpty();
	}
}
