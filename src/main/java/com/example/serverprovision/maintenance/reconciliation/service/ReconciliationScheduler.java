package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * MK4-3-2 — <b>언제 점검할지</b>만 아는 곳. 점검이 무엇을 하는지는 {@link PathReconciliationService} 가 안다.
 *
 * <h2>왜 짧은 심박인가</h2>
 * <p>종전에는 {@code @Scheduled(fixedRateString = "${reconciliation.scan.interval-ms}")} 두 개가 주기를
 * 들고 있었다. 이 문자열은 빈을 만들 때 한 번 해석되고 굳으므로 주기를 바꾸려면 설정 파일을 고치고
 * 애플리케이션을 다시 띄워야 했다.</p>
 *
 * <p>Spring 이 이런 경우를 위해 둔 확장점은 {@code Trigger} 다. 다만 {@code Trigger.nextExecution} 은
 * 매 실행 <b>직후</b>에만 호출되므로 이미 대기 중인 발동은 옛 값으로 잡혀 있고 저장이 그것을 깨우지
 * 못한다 — 정밀 주기를 24 시간에서 1 시간으로 줄여도 최대 하루를 기다린다. 그래서 프레임워크에는
 * <b>변하지 않는 부분</b>(1 분마다 깨워 달라)만 맡기고 <b>변하는 부분</b>(지금이 점검할 때인가)은
 * 도메인이 쥔다. 판정이 순수 함수({@link ScanSchedule#dueDepth})라 스케줄러를 구동하지 않고 검증된다.</p>
 *
 * <h2>이 구조가 없앤 것</h2>
 * <ul>
 *   <li><b>재기동마다 주기가 다시 처음부터</b> — 옛 구조는 {@code initialDelay} 가 주기와 같아 기동 후
 *       첫 정밀 점검이 24 시간 뒤였다. 하루 안에 재기동이 있으면 정밀 점검은 영영 돌지 않았다.
 *       여기서는 기준 시각을 보고서에서 읽으므로 밀렸으면 뜨자마자 돈다.</li>
 *   <li><b>겹치면 정밀이 버려짐</b> — 옛 구조는 두 스케줄이 24 시간마다 같은 순간에 겹쳤고, 스케줄러
 *       스레드가 하나라 뒤에 온 정밀 점검이 동시 실행 가드에 막혀 로그 한 줄만 남기고 사라졌다.
 *       한 번의 심박이 하나의 결정만 내리므로 경합할 자리가 없다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

	/**
	 * 심박 간격. 설정으로 열지 않는다 — 누군가 1 시간으로 두면 "저장은 1 분 안에 반영된다" 는 약속이
	 * 조용히 깨진다. 주기의 저장 단위를 분으로 둔 것도 이 값과 맞추기 위해서다.
	 */
	private static final long TICK_INTERVAL_MS = 60_000L;

	private final PathReconciliationService reconciliationService;
	private final ReconciliationSettingsService settingsService;
	private final DriftReportRepository driftReportRepository;

	/**
	 * 기동 직후 1 회. <b>일반 점검으로 돈다</b> — 이 항목의 뜻이 "꺼져 있는 동안 디스크가 바뀌었을 수
	 * 있어 한 번 맞춰 본다" 이고 마커와 위치를 보는 것으로 그 물음에 답이 되기 때문이다. 정밀 점검이
	 * 밀려 있다면 1 분 뒤 첫 심박이 판정해 돌리므로 기동 점검이 그것까지 떠안을 이유가 없다.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		if (!settingsService.isStartupScanEnabled()) {
			log.info("[reconciliation] 기동 직후 점검 비활성 — 운영 설정에서 꺼져 있음");
			return;
		}
		trigger(ScanDepth.QUICK, "기동 직후");
	}

	/**
	 * 심박. 매번 설정과 마지막 점검 시각을 다시 읽으므로 저장이 다음 박동에 반영된다.
	 *
	 * <p>첫 박동을 1 분 뒤로 미루는 이유는 기동 직후 점검과 겹치지 않게 하기 위해서다. 겹쳐도 동시 실행
	 * 가드가 막지만, 막히는 것과 애초에 안 겹치는 것은 다르다.</p>
	 */
	@Scheduled(fixedDelay = TICK_INTERVAL_MS, initialDelay = TICK_INTERVAL_MS)
	public void tick() {
		if (reconciliationService.isScanRunning()) {
			log.debug("[reconciliation] 심박 건너뜀 — 이전 점검이 아직 돌고 있음");
			return;
		}
		Optional<ScanDepth> due = currentSchedule().dueDepth(Instant.now());
		due.ifPresent(depth -> trigger(depth, "주기 도래"));
	}

	private void trigger(ScanDepth depth, String reason) {
		try {
			reconciliationService.triggerScan(depth);
			log.info("[reconciliation] {} — {} 시작", reason, depth.getLabel());
		} catch (ReconciliationAlreadyRunningException ignored) {
			// 판정과 시작 사이에 수동 점검이 끼어든 경우. 다음 박동이 다시 본다.
			log.debug("[reconciliation] {} 판정 후 이미 실행 중이라 건너뜀", reason);
		}
	}

	/**
	 * 지금의 점검 일정. 심박이 쓰고, 설정 화면이 <b>다음 점검 예정 시각</b>을 그리는 데도 쓴다.
	 *
	 * <p>일반 점검의 기준 시각은 정밀을 포함한 마지막 점검이고 정밀 점검의 기준 시각은 마지막 정밀
	 * 점검이다. 정밀이 일반을 덮으므로 정밀은 일반의 시계도 되돌리지만 반대는 아니다. 수동 점검도
	 * 보고서를 남기므로 자연히 기준에 든다.</p>
	 */
	/**
	 * 한 깊이의 다음 점검 예정 시각. 목록 화면이 커버리지 안내 옆에 붙이는 값이라 그것만 묻는다 —
	 * 일정 전체를 넘겨 화면이 파고 들어가게 하면 화면이 일정의 구조를 알아야 한다.
	 */
	@Transactional(readOnly = true)
	public Optional<Instant> nextDueAt(ScanDepth depth) {
		return currentSchedule().of(depth).nextDueAt();
	}

	@Transactional(readOnly = true)
	public ScanSchedule currentSchedule() {
		DriftReport lastAny = driftReportRepository.findFirstByOrderByScannedAtDesc().orElse(null);
		DriftReport lastDeep = driftReportRepository.findFirstByDeepTrueOrderByScannedAtDesc().orElse(null);
		DriftReport lastQuick = driftReportRepository.findFirstByDeepFalseOrderByScannedAtDesc().orElse(null);
		return new ScanSchedule(
				new ScanSchedule.DepthState(
						settingsService.scanInterval(), scannedAt(lastAny), durationOf(lastQuick)),
				new ScanSchedule.DepthState(
						settingsService.deepScanInterval(), scannedAt(lastDeep), durationOf(lastDeep)));
	}

	private static Instant scannedAt(DriftReport report) {
		return report == null ? null : report.getScannedAt();
	}

	private static Duration durationOf(DriftReport report) {
		return report == null ? null : Duration.ofMillis(report.getScanDurationMs());
	}
}
