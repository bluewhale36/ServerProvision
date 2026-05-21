package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.trash.dto.request.TrashSettingsRequest;
import com.example.serverprovision.global.trash.dto.response.TrashSettingsResponse;
import com.example.serverprovision.global.trash.enums.NotifyChannel;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * S5-2-4 — 휴지통 운영 설정 (trash_settings) singleton row 접근 / 갱신 인터페이스.
 *
 * <p><strong>Singleton 보장</strong> : id 값 고정 (1L) 대신 {@code count() == 1} 검사 기반.
 * row 가 0개면 default 시드 insert, 2개 이상이면 첫 row 만 신뢰 + 경고 로그 (사용자 결정 2026-05-20).</p>
 *
 * <p>CP2 — 시그니처만. 본체 알고리즘 (cron 변경 즉시 worker 반영, 콤마 split 변환 등) 은 CP4.</p>
 */
public interface TrashSettingsService {

	/**
	 * 현재 설정 응답. count==0 이면 default 시드 후 응답, count>=2 이면 첫 row + log.warn.
	 */
	TrashSettingsResponse current();

	/**
	 * 운영자 갱신. count!=1 이면 정리 후 갱신 적용 (CP4 본체에서 상세 정책).
	 */
	TrashSettingsResponse update(TrashSettingsRequest request);

	/**
	 * worker / executor 가 자주 호출하는 직접 접근 — TTL 계산용.
	 */
	Duration getTtl();

	/**
	 * TTL_AUTO 의 retry 정책.
	 */
	int getRetryMaxAttempts();

	/**
	 * backoff base ms.
	 */
	long getRetryBackoffBaseMs();

	/**
	 * auto_purge_enabled flag — TrashTtlWorker 가 cron 진입 직후 가드.
	 */
	boolean isAutoPurgeEnabled();

	/**
	 * purge_cron_expression — Spring Scheduler 가 본 값을 cron 으로 사용. CP4 본체에서 동적 cron 어떻게 적용할지 결정.
	 */
	String getPurgeCronExpression();

	/**
	 * notify_cron_expression.
	 */
	String getNotifyCronExpression();

	/**
	 * notify_days_before split → int 리스트.
	 */
	List<Integer> getNotifyDaysBeforeList();

	/**
	 * notification_channels split → enum 셋.
	 */
	Set<NotifyChannel> getNotificationChannels();
}
