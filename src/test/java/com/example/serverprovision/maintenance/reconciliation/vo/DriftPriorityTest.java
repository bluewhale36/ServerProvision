package com.example.serverprovision.maintenance.reconciliation.vo;

import com.example.serverprovision.global.marker.DriftSeverity;
import com.example.serverprovision.provisioning.usage.ResourceUsageLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MK4-2 — 처리 순서 규칙. 이 파일이 고정하는 것은 <b>사전식 비교</b>와 그 결과인 <b>급 보존</b>이다.
 *
 * <p>급 보존이 깨지면 화면이 "위험도 → 사용 중 → 오래된 순" 이라고 설명해 놓고 다른 순서를 보여주게
 * 된다. 가중합으로 바꾸려는 시도가 있으면 이 파일이 먼저 실패해야 한다.</p>
 */
class DriftPriorityTest {

	private static final Instant OLD = Instant.parse("2026-07-01T00:00:00Z");
	private static final Instant NEW = Instant.parse("2026-08-01T00:00:00Z");

	private static DriftPriority p(DriftSeverity severity, ResourceUsageLevel usage, Instant at) {
		return new DriftPriority(severity, usage, at);
	}

	@Nested
	@DisplayName("큰 축 — 위험도가 급을 가른다")
	class SeverityFirst {

		@Test
		@DisplayName("더 급한 위험도가 앞선다")
		void moreSevereComesFirst() {
			assertThat(p(DriftSeverity.IMMEDIATE, ResourceUsageLevel.NONE, NEW))
					.isLessThan(p(DriftSeverity.ATTENTION, ResourceUsageLevel.NONE, NEW));
		}

		@Test
		@DisplayName("급 보존 — 사용 중을 최대로 올려도 상위 급을 넘지 못한다")
		void usageNeverCrossesSeverityClass() {
			DriftPriority tidyButRunning = p(DriftSeverity.TIDY, ResourceUsageLevel.RUNNING, OLD);
			DriftPriority immediateUnused = p(DriftSeverity.IMMEDIATE, ResourceUsageLevel.NONE, NEW);

			// 사용 중도 최대이고 더 오래되기까지 했지만 급이 낮으므로 뒤에 온다.
			assertThat(tidyButRunning).isGreaterThan(immediateUnused);
		}

		@Test
		@DisplayName("모든 급 조합에서 급 보존이 성립한다")
		void classPreservationHoldsForEveryPair() {
			for (DriftSeverity higher : DriftSeverity.values()) {
				for (DriftSeverity lower : DriftSeverity.values()) {
					if (higher.ordinal() >= lower.ordinal()) continue;
					DriftPriority weakestOfHigher = p(higher, ResourceUsageLevel.NONE, NEW);
					DriftPriority strongestOfLower = p(lower, ResourceUsageLevel.RUNNING, OLD);
					assertThat(weakestOfHigher)
							.as("%s(최약) 가 %s(최강) 보다 앞서야 한다", higher, lower)
							.isLessThan(strongestOfLower);
				}
			}
		}
	}

	@Nested
	@DisplayName("보정 축 — 같은 급 안에서 사용 중이 순서를 바꾼다")
	class UsageWithinClass {

		@Test
		@DisplayName("더 깊이 쓰이는 쪽이 앞선다")
		void deeperUsageComesFirst() {
			assertThat(p(DriftSeverity.ATTENTION, ResourceUsageLevel.RUNNING, NEW))
					.isLessThan(p(DriftSeverity.ATTENTION, ResourceUsageLevel.ASSIGNED, NEW));
			assertThat(p(DriftSeverity.ATTENTION, ResourceUsageLevel.ASSIGNED, NEW))
					.isLessThan(p(DriftSeverity.ATTENTION, ResourceUsageLevel.DEFINED, NEW));
			assertThat(p(DriftSeverity.ATTENTION, ResourceUsageLevel.DEFINED, NEW))
					.isLessThan(p(DriftSeverity.ATTENTION, ResourceUsageLevel.NONE, NEW));
		}
	}

	@Nested
	@DisplayName("동률 — 오래 방치된 것이 앞선다")
	class TieBreak {

		@Test
		@DisplayName("위험도와 사용 중이 같으면 최초 발견이 이른 쪽이 앞선다")
		void olderFirst() {
			assertThat(p(DriftSeverity.RECORD, ResourceUsageLevel.DEFINED, OLD))
					.isLessThan(p(DriftSeverity.RECORD, ResourceUsageLevel.DEFINED, NEW));
		}

		@Test
		@DisplayName("최초 발견이 없는 값은 뒤로 밀되 비교가 깨지지 않는다")
		void nullDetectedAtSortsLast() {
			DriftPriority unknown = p(DriftSeverity.RECORD, ResourceUsageLevel.NONE, null);
			DriftPriority known = p(DriftSeverity.RECORD, ResourceUsageLevel.NONE, NEW);
			assertThat(known).isLessThan(unknown);
		}
	}

	@Nested
	@DisplayName("도메인 불변식")
	class Invariants {

		@Test
		@DisplayName("위험도 없이는 순서를 만들 수 없다")
		void severityRequired() {
			assertThatThrownBy(() -> p(null, ResourceUsageLevel.NONE, NEW))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("사용 수준 없이는 순서를 만들 수 없다")
		void usageRequired() {
			assertThatThrownBy(() -> p(DriftSeverity.TIDY, null, NEW))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	@DisplayName("실제 정렬 — 계획서 데모와 같은 결과가 나온다")
	void sortsLikeThePlanDemo() {
		List<DriftPriority> list = new ArrayList<>(List.of(
				p(DriftSeverity.TIDY, ResourceUsageLevel.RUNNING, OLD),          // 미아 마커, 진행 중
				p(DriftSeverity.ATTENTION, ResourceUsageLevel.NONE, NEW),        // 자원 중복, 미사용
				p(DriftSeverity.IMMEDIATE, ResourceUsageLevel.NONE, NEW),        // 자원 소실, 미사용
				p(DriftSeverity.IMMEDIATE, ResourceUsageLevel.ASSIGNED, NEW)     // 내용 변경, 서버 할당
		));
		list.sort(Comparator.naturalOrder());

		assertThat(list).containsExactly(
				p(DriftSeverity.IMMEDIATE, ResourceUsageLevel.ASSIGNED, NEW),
				p(DriftSeverity.IMMEDIATE, ResourceUsageLevel.NONE, NEW),
				p(DriftSeverity.ATTENTION, ResourceUsageLevel.NONE, NEW),
				p(DriftSeverity.TIDY, ResourceUsageLevel.RUNNING, OLD));
	}
}
