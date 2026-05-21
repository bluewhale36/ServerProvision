package com.example.serverprovision.global.trash.dto.response;

import java.time.Instant;

/**
 * S5-2-4 — trash_settings 조회 응답. {@code /maintenance/trash/settings} GET 응답.
 */
public record TrashSettingsResponse(
		int ttlDays,
		boolean autoPurgeEnabled,
		String purgeCronExpression,
		String notifyCronExpression,
		String notifyDaysBefore,
		String notificationChannels,
		int retryMaxAttempts,
		long retryBackoffBaseMs,
		Instant updatedAt
) {

}
