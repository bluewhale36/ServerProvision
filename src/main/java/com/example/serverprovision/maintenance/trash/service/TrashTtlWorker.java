package com.example.serverprovision.maintenance.trash.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.service.NotificationDispatcher;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.global.trash.service.TrashSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * S5-2-4 CP4 — TTL 30일 경과 자원 자동 hard-delete worker.
 *
 * <p>{@link MarkableScanner} SPI 다형성 활용 — 도메인별 scanner 가 자기 trashed 자원 조회 / 만료
 * 검사를 수행하고 본 worker 는 합산만. PurgeExecutor 위임으로 retry / log INSERT 인프라 공유.</p>
 *
 * <p>실패 자원은 휴지통에 남아있으므로 다음 cron tick 에서 자연스럽게 재시도 후보로 식별 —
 * 별도 retry-후보-조회 SPI 불필요 (사용자 결정 v3 D8).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashTtlWorker {

	private final TrashSettingsService settingsService;
	private final List<MarkableScanner> scanners;
	private final PurgeExecutor purgeExecutor;
	private final NotificationDispatcher notificationDispatcher;

	/**
	 * TTL 만료 자원 식별 + PurgeExecutor 호출.
	 *
	 * <p>cron 식은 trash_settings.purge_cron_expression — 단, 본 슬라이스에서는 @Scheduled 의 cron
	 * fixed-string 한계로 default 만 적용 (CP5 후속에서 동적 cron 검토).</p>
	 */
	@Scheduled(cron = "${trash.ttl.purge-cron:0 0 * * * *}")
	public void purgeExpired() {
		if (!settingsService.isAutoPurgeEnabled()) {
			log.debug("[trash:ttl] auto_purge_enabled=false — purgeExpired skip");
			return;
		}
		Instant threshold = Instant.now().minus(settingsService.getTtl());
		int totalAttempted = 0;
		int totalSuccess = 0;
		for (MarkableScanner scanner : scanners) {
			List<Markable> expired = scanner.findTrashedBefore(threshold);
			if (expired.isEmpty()) continue;
			log.info(
					"[trash:ttl] type={} expired count={} threshold={}",
					scanner.supportedType(), expired.size(), threshold
			);
			for (Markable m : expired) {
				totalAttempted++;
				try {
					var result = purgeExecutor.execute(
							PurgeRequest.forTtlAuto(scanner.supportedType(), m.getResourceId()));
					if (result instanceof com.example.serverprovision.global.trash.PurgeResult.Success) {
						totalSuccess++;
					} else {
						// FAILED 자원 단위 알림 격상 — cron tick 내 retry 3회 모두 실패 의미
						notificationDispatcher.dispatch(
								JobType.TRASH_PURGE_FAILED,
								settingsService.getNotificationChannels(),
								"영구삭제 실패 — " + m.displayName(),
								"다음 cron 자동 재시도 예정 (또는 운영자 수동 retry)."
						);
					}
				} catch (Exception ex) {
					log.error(
							"[trash:ttl] PurgeExecutor 호출 자체가 실패. type={} id={} cause={}",
							scanner.supportedType(), m.getResourceId(), ex.toString(), ex
					);
				}
			}
		}
		if (totalAttempted > 0) {
			log.info("[trash:ttl] 자동 영구삭제 완료. attempted={} success={}", totalAttempted, totalSuccess);
		}
	}

	/**
	 * TTL 임박 사전 알림 — settings.notify_days_before 의 D-day 별 자원 알림.
	 */
	@Scheduled(cron = "${trash.ttl.notify-cron:0 0 * * * *}")
	public void notifyUpcomingExpiration() {
		Instant now = Instant.now();
		Duration ttl = settingsService.getTtl();
		for (int d : settingsService.getNotifyDaysBeforeList()) {
			Instant start = now.minus(ttl).plus(Duration.ofDays(d - 1));
			Instant end = now.minus(ttl).plus(Duration.ofDays(d));
			for (MarkableScanner scanner : scanners) {
				List<Markable> targets = scanner.findTrashedBetween(start, end);
				if (targets.isEmpty()) continue;
				log.info("[trash:ttl] type={} D-{} 임박 자원 {}건", scanner.supportedType(), d, targets.size());
				for (Markable m : targets) {
					notificationDispatcher.dispatch(
							JobType.TTL_NOTIFY,
							settingsService.getNotificationChannels(),
							d + "일 후 영구삭제 예정 — " + m.displayName(),
							"필요하다면 휴지통에서 복원하거나 보존기간을 연장하세요."
					);
				}
			}
		}
	}
}
