package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.dto.response.BulkApplyResponse;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftObservation;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftHandlingAction;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.enums.SnoozeWindow;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftReportNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanPopulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

/**
 * MK4-4-2 — 여러 문제를 한 번에 해결한다.
 *
 * <p>이 서비스가 지켜야 할 것은 셋이다. ① 대상 선별이 개별 [해결] 버튼의 활성 조건과 <b>같은
 * 판정</b>을 볼 것 ② 한 건이 실패해도 <b>멈추지 않을</b> 것 ③ 실패를 삼키지 말고 <b>결과로
 * 돌려줄</b> 것. 셋 중 하나라도 어긋나면 사용자가 목록을 눈으로 세어 확인해야 한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class DriftBulkApplyServiceTest {

	@Mock PathReconciliationService reconciliationService;
	@Mock DriftRepository driftRepository;
	@Mock DriftReportRepository driftReportRepository;

	@InjectMocks DriftBulkApplyService service;

	/**
	 * 상대 기준시각 — 고정 시각을 쓰면 보관(snooze) 창이 실제 달력에서 만료되는 순간부터
	 * 영구 실패하는 시한폭탄이 된다(2026-08-12 고정 + 7일 창이 08-19 에 실제로 발화했던 선례).
	 * 대상 서비스가 만료 판정에 실 시계를 쓰므로, 기준시각도 실행 시점 기준이어야 창이 항상 미래다.
	 */
	private static final Instant NOW = Instant.now();

	private DriftReport report;

	@BeforeEach
	void setUp() {
		report = DriftReport.builder()
				.scannedAt(NOW).scanDurationMs(100).deep(false)
				.population(ScanPopulation.of(3, 0, 0))
				.build();
		ReflectionTestUtils.setField(report, "id", 7L);
	}

	private static DriftObservation observationOf(Drift drift, Instant observedAt) {
		return DriftObservation.builder()
				.drift(drift).observedAt(observedAt)
				.oldPath(drift.getOldPath()).newPath(drift.getNewPath())
				.detail(drift.getDetail()).observedHash(drift.getObservedHash())
				.build();
	}

	/** 스캔이 {@code linkObservations} 에서 만드는 것과 같은 모양으로 문제 하나를 회차에 얹는다. */
	private Drift driftOn(long id, DriftKind kind) {
		Drift drift = Drift.builder()
				.resourceType(ResourceType.OS_ISO).resourceId(id).kind(kind)
				.oldPath("/iso/" + id + ".iso")
				.firstDetectedAt(NOW).lastObservedAt(NOW)
				.build();
		ReflectionTestUtils.setField(drift, "id", id);
		report.addObservation(observationOf(drift, NOW));
		return drift;
	}

	@Nested
	@DisplayName("대상 선별 — 개별 [해결] 과 같은 판정을 본다")
	class Targets {

		@Test
		@DisplayName("회차에서 시스템이 해결할 수 있는 것만 집는다")
		void 회차_대상_선별() {
			driftOn(1L, DriftKind.PATH_DRIFT);      // 시스템 해결 가능
			driftOn(2L, DriftKind.HASH_MISMATCH);   // 사용자 확인이 필요한 전용 계약 — 일괄 대상 아님
			given(driftReportRepository.findById(7L)).willReturn(Optional.of(report));

			assertThat(service.targetsInReport(7L)).containsExactly(1L);
		}

		@Test
		@DisplayName("이미 해결된 것은 집지 않는다 — 지난 회차를 열어도 다시 처리되지 않는다")
		void 이미_해결된_것_제외() {
			Drift resolved = driftOn(1L, DriftKind.PATH_DRIFT);
			resolved.resolve(NOW, DriftHandlingAction.APPLY);
			driftOn(2L, DriftKind.PATH_DRIFT);
			given(driftReportRepository.findById(7L)).willReturn(Optional.of(report));

			assertThat(service.targetsInReport(7L)).containsExactly(2L);
		}

		/**
		 * 한 문제가 여러 회차에 관측돼도 문제는 하나다. 회차가 직접 추려 주므로 여기서 두 번 세지
		 * 않는다 — 두 번 세면 같은 문제에 해결이 두 번 걸린다.
		 */
		@Test
		@DisplayName("같은 문제가 두 번 관측돼도 한 번만 집는다")
		void 중복_관측_한_번() {
			Drift drift = driftOn(1L, DriftKind.PATH_DRIFT);
			report.addObservation(observationOf(drift, NOW.plusSeconds(60)));
			given(driftReportRepository.findById(7L)).willReturn(Optional.of(report));

			assertThat(service.targetsInReport(7L)).containsExactly(1L);
		}

		@Test
		@DisplayName("없는 회차 → 404 예외")
		void 없는_회차() {
			given(driftReportRepository.findById(999L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> service.targetsInReport(999L))
					.isInstanceOf(DriftReportNotFoundException.class)
					.hasMessageContaining("999");
		}

		@Test
		@DisplayName("열린 것 전체 — 보관 중인 것은 목록에 없으므로 집지 않는다")
		void 열린_것_선별() {
			Drift open = driftOn(1L, DriftKind.PATH_DRIFT);
			Drift snoozed = driftOn(2L, DriftKind.PATH_DRIFT);
			snoozed.snooze(SnoozeWindow.DAYS_7, "다음 주에 본다", NOW);
			given(driftRepository.findByStatusNot(DriftStatus.RESOLVED))
					.willReturn(List.of(open, snoozed));

			assertThat(service.openTargets()).containsExactly(1L);
		}
	}

	@Nested
	@DisplayName("실행 — 한 건이 실패해도 멈추지 않는다")
	class ApplyAll {

		@Test
		@DisplayName("전부 성공하면 집은 수와 해결한 수가 같다")
		void 전부_성공() {
			doNothing().when(reconciliationService).apply(anyLong());

			BulkApplyResponse result = service.applyAll(List.of(1L, 2L, 3L));

			assertThat(result.requested()).isEqualTo(3);
			assertThat(result.applied()).isEqualTo(3);
			assertThat(result.allApplied()).isTrue();
			then(reconciliationService).should(times(3)).apply(anyLong());
		}

		/**
		 * 여기가 이 서비스의 존재 이유다. 한 트랜잭션이면 마지막 실패가 앞의 성공을 되돌리는데,
		 * 파일을 이미 옮긴 뒤라 그 되돌리기는 오히려 새 불일치를 만든다.
		 */
		@Test
		@DisplayName("가운데 한 건이 실패해도 나머지를 마저 처리한다")
		void 부분_실패_후_계속() {
			doNothing().when(reconciliationService).apply(1L);
			doThrow(DriftResolutionNotAllowedException.of("이미 해결된 드리프트입니다."))
					.when(reconciliationService).apply(2L);
			doNothing().when(reconciliationService).apply(3L);

			BulkApplyResponse result = service.applyAll(List.of(1L, 2L, 3L));

			assertThat(result.applied()).isEqualTo(2);
			assertThat(result.failed()).isEqualTo(1);
			assertThat(result.allApplied()).isFalse();
			// 실패를 삼키지 않는다 — 무엇이 왜 남았는지 화면이 말할 수 있어야 한다.
			assertThat(result.failures()).singleElement().asString()
					.contains("#2").contains("이미 해결된");
			then(reconciliationService).should(times(3)).apply(anyLong());
		}

		@Test
		@DisplayName("집은 것이 없으면 아무것도 부르지 않는다")
		void 대상_없음() {
			BulkApplyResponse result = service.applyAll(List.of());

			assertThat(result.requested()).isZero();
			assertThat(result.applied()).isZero();
			assertThat(result.allApplied()).isTrue();
			then(reconciliationService).shouldHaveNoInteractions();
		}
	}
}
