package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftHandlingAction;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.enums.SnoozeWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MK4-4-3 — 보관 상태 전이.
 *
 * <p>보관은 걸기 · 풀기 · 만료 셋으로 오간다. 그 판정이 도메인 메서드 하나에 모여 있어야 화면의
 * 버튼 비활성 조건과 서버 가드가 갈라지지 않는다.</p>
 */
class DriftSnoozeStateTest {

	private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

	private Drift open() {
		return Drift.builder()
				.resourceType(ResourceType.OS_ISO).resourceId(1L).kind(DriftKind.PATH_DRIFT)
				.oldPath("/iso/a.iso").firstDetectedAt(NOW).lastObservedAt(NOW)
				.build();
	}

	@Nested
	@DisplayName("보관을 풀 수 있는가")
	class UnsnoozeGuard {

		@Test
		@DisplayName("보관 중이면 풀 수 있다")
		void 보관_중이면_가능() {
			Drift drift = open();
			drift.snooze(SnoozeWindow.DAYS_7, "다음 주에 본다", NOW);

			assertThat(drift.unsnoozeBlockReason()).isNull();
		}

		/** 보관 목록에만 [보관 해제] 가 있으므로 정상 흐름에서는 도달하지 않는 안전망이다. */
		@Test
		@DisplayName("열려 있는 것은 풀 것이 없다")
		void 열린_것은_불가() {
			assertThat(open().unsnoozeBlockReason()).isEqualTo("보관 중인 드리프트가 아닙니다.");
		}

		@Test
		@DisplayName("이미 해결된 것은 풀 수 없다")
		void 해결된_것은_불가() {
			Drift drift = open();
			drift.resolve(NOW, DriftHandlingAction.APPLY);

			assertThat(drift.unsnoozeBlockReason()).isEqualTo("이미 해결된 드리프트입니다.");
		}

		/**
		 * 만료된 보관은 이미 첫 화면에 돌아와 있지만 상태 값은 아직 보관이다. 여기서 풀어 기록을
		 * 맞추는 것이 정상이라 막지 않는다.
		 */
		@Test
		@DisplayName("만료된 보관은 막지 않는다 — 기록을 맞추는 일이다")
		void 만료된_보관도_가능() {
			Drift drift = open();
			drift.snooze(SnoozeWindow.DAYS_7, "미룬다", NOW);

			assertThat(drift.isSnoozeExpired(NOW.plusSeconds(8 * 86400))).isTrue();
			assertThat(drift.unsnoozeBlockReason()).isNull();
		}
	}

	@Nested
	@DisplayName("전이가 남기는 것")
	class Transition {

		@Test
		@DisplayName("풀면 열린 상태로 돌아가고 보관에 딸린 값 셋이 함께 비워진다")
		void 해제하면_셋이_비워진다() {
			Drift drift = open();
			drift.snooze(SnoozeWindow.DAYS_7, "다음 주에 본다", NOW);

			drift.reopen();

			assertThat(drift.getStatus()).isEqualTo(DriftStatus.OPEN);
			assertThat(drift.getSnoozeUntil()).isNull();
			assertThat(drift.getSnoozeWindow()).isNull();
			assertThat(drift.getSnoozeReason()).isNull();
		}

		/**
		 * MK4-4-2 가 고친 결함의 회귀 방지 — 종전에는 resolve 가 사유를 남겨, 해결된 드리프트의
		 * 상태 줄에 지난 보관 사유가 붙었다.
		 */
		@Test
		@DisplayName("해결로 닫아도 보관 사유가 남지 않는다")
		void 해결해도_사유가_남지_않는다() {
			Drift drift = open();
			drift.snooze(SnoozeWindow.DAYS_7, "다음 주에 본다", NOW);

			drift.resolve(NOW.plusSeconds(60), DriftHandlingAction.APPLY);

			assertThat(drift.getStatus()).isEqualTo(DriftStatus.RESOLVED);
			assertThat(drift.getSnoozeReason()).isNull();
			assertThat(drift.getSnoozeWindow()).isNull();
		}
	}
}
