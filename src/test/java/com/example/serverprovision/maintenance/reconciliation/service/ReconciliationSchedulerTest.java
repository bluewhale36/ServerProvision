package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MK4-3-2 — 심박이 무엇을 언제 시작하는가.
 *
 * <p>여기서 고정하는 것은 종전 구조가 잃던 두 가지다. ① 두 주기가 같은 순간에 겹쳐도 정밀 점검이
 * 버려지지 않는다 ② 재기동해도 밀린 정밀 점검이 사라지지 않는다. 둘 다 심박이 <b>한 번에 하나의
 * 결정</b>만 내리고 기준 시각을 보고서에서 읽기 때문에 성립한다.</p>
 */
class ReconciliationSchedulerTest {

	private static final Instant NOW = Instant.now();

	private PathReconciliationService reconciliationService;
	private ReconciliationSettingsService settingsService;
	private DriftReportRepository driftReportRepository;
	private ReconciliationScheduler scheduler;

	@BeforeEach
	void setUp() {
		reconciliationService = Mockito.mock(PathReconciliationService.class);
		settingsService = Mockito.mock(ReconciliationSettingsService.class);
		driftReportRepository = Mockito.mock(DriftReportRepository.class);

		given(settingsService.scanInterval()).willReturn(ScanInterval.ofMinutes(60));
		given(settingsService.deepScanInterval()).willReturn(ScanInterval.ofMinutes(1440));
		when(driftReportRepository.findFirstByOrderByScannedAtDesc()).thenReturn(Optional.empty());
		when(driftReportRepository.findFirstByDeepTrueOrderByScannedAtDesc()).thenReturn(Optional.empty());
		when(driftReportRepository.findFirstByDeepFalseOrderByScannedAtDesc()).thenReturn(Optional.empty());

		scheduler = new ReconciliationScheduler(
				reconciliationService, settingsService, driftReportRepository);
	}

	/** 보고서 한 건. 스캔 시각과 소요 시간만 쓰므로 그 둘만 심는다. */
	private static DriftReport report(Instant scannedAt, boolean deep, long durationMs) {
		DriftReport report = DriftReport.builder().deep(deep).build();
		ReflectionTestUtils.setField(report, "scannedAt", scannedAt);
		ReflectionTestUtils.setField(report, "scanDurationMs", durationMs);
		return report;
	}

	private void givenLastScans(Instant lastAny, Instant lastDeep) {
		when(driftReportRepository.findFirstByOrderByScannedAtDesc())
				.thenReturn(Optional.ofNullable(lastAny).map(t -> report(t, false, 1_000)));
		when(driftReportRepository.findFirstByDeepTrueOrderByScannedAtDesc())
				.thenReturn(Optional.ofNullable(lastDeep).map(t -> report(t, true, 5_000)));
	}

	@Test
	@DisplayName("아무것도 밀리지 않았으면 점검을 시작하지 않는다")
	void nothingDue_doesNotTrigger() {
		givenLastScans(NOW, NOW);

		scheduler.tick();

		verify(reconciliationService, never()).triggerScan(any());
	}

	@Test
	@DisplayName("일반만 밀렸으면 일반 점검")
	void quickDue_triggersQuick() {
		givenLastScans(NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2)));

		scheduler.tick();

		verify(reconciliationService).triggerScan(ScanDepth.QUICK);
	}

	/**
	 * 종전 구조는 두 {@code @Scheduled} 가 24 시간마다 같은 순간에 겹쳤고, 스케줄러 스레드가 하나라
	 * 뒤에 온 정밀 점검이 동시 실행 가드에 막혀 로그 한 줄만 남기고 사라졌다. 한 번의 심박이 하나의
	 * 결정만 내리므로 그 경합이 구조적으로 없어졌다.
	 */
	@Test
	@DisplayName("둘 다 밀렸으면 정밀 한 번만 — 겹쳐서 버려지는 일이 없다")
	void bothDue_triggersDeepOnce() {
		givenLastScans(NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofDays(2)));

		scheduler.tick();

		verify(reconciliationService).triggerScan(ScanDepth.DEEP);
		verify(reconciliationService, never()).triggerScan(ScanDepth.QUICK);
	}

	/**
	 * 종전에는 {@code initialDelay} 가 주기와 같아 기동 후 첫 정밀 점검이 24 시간 뒤였다. 하루 안에
	 * 재기동이 있으면 정밀 점검은 영영 돌지 않았다.
	 */
	@Test
	@DisplayName("재기동 직후라도 밀린 정밀 점검은 즉시 돈다")
	void overdueDeepRunsRightAfterRestart() {
		givenLastScans(NOW.minus(Duration.ofMinutes(1)), NOW.minus(Duration.ofDays(7)));

		scheduler.tick();

		verify(reconciliationService).triggerScan(ScanDepth.DEEP);
	}

	@Test
	@DisplayName("이미 점검 중이면 아무것도 하지 않는다 — 예외를 흐름 제어로 쓰지 않는다")
	void skipsWhileRunning() {
		given(reconciliationService.isScanRunning()).willReturn(true);
		givenLastScans(null, null);

		scheduler.tick();

		verify(reconciliationService, never()).triggerScan(any());
	}

	@Test
	@DisplayName("판정과 시작 사이에 수동 점검이 끼어들면 조용히 양보한다")
	void yieldsWhenRaceLost() {
		givenLastScans(null, null);
		given(reconciliationService.triggerScan(any()))
				.willThrow(new ReconciliationAlreadyRunningException());

		scheduler.tick();   // 예외가 밖으로 새면 스케줄러가 죽는다

		verify(reconciliationService).triggerScan(ScanDepth.DEEP);
	}

	@Test
	@DisplayName("기동 직후 점검은 일반으로 돈다 — 정밀은 첫 심박이 판정한다")
	void startupScanIsQuick() {
		given(settingsService.isStartupScanEnabled()).willReturn(true);

		scheduler.onStartup();

		verify(reconciliationService).triggerScan(ScanDepth.QUICK);
	}

	@Test
	@DisplayName("기동 직후 점검이 꺼져 있으면 돌지 않는다")
	void startupScanRespectsSetting() {
		given(settingsService.isStartupScanEnabled()).willReturn(false);

		scheduler.onStartup();

		verify(reconciliationService, never()).triggerScan(any());
	}

	@Test
	@DisplayName("다음 예정 시각은 마지막 점검 + 주기 — 화면이 이 값을 그대로 보여 준다")
	void scheduleExposesNextDueTime() {
		Instant lastDeep = NOW.minus(Duration.ofHours(1));
		givenLastScans(NOW, lastDeep);

		var schedule = scheduler.currentSchedule();

		assertThat(schedule.quick().nextDueAt()).contains(NOW.plus(Duration.ofMinutes(60)));
		assertThat(schedule.deep().nextDueAt()).contains(lastDeep.plus(Duration.ofMinutes(1440)));
	}

	@Test
	@DisplayName("경고 근거는 같은 깊이의 실측 소요 시간이다 — 정밀 시간을 일반에 갖다 쓰지 않는다")
	void warningUsesSameDepthDuration() {
		when(driftReportRepository.findFirstByDeepFalseOrderByScannedAtDesc())
				.thenReturn(Optional.of(report(NOW, false, 2_000)));
		when(driftReportRepository.findFirstByDeepTrueOrderByScannedAtDesc())
				.thenReturn(Optional.of(report(NOW, true, 252_000)));
		when(driftReportRepository.findFirstByOrderByScannedAtDesc())
				.thenReturn(Optional.of(report(NOW, true, 252_000)));

		var schedule = scheduler.currentSchedule();

		assertThat(schedule.quick().lastDuration()).isEqualTo(Duration.ofSeconds(2));
		assertThat(schedule.deep().lastDuration()).isEqualTo(Duration.ofSeconds(252));
	}
}
