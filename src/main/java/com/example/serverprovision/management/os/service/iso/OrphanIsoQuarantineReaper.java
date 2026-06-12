package com.example.serverprovision.management.os.service.iso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TTL 만료된 PENDING 격리(오펀 ISO)를 주기적으로 자동 폐기하는 reaper.
 * 실제 로직은 {@link OrphanIsoRecoveryService#purgeExpired()} 에 위임 — 본 클래스는 스케줄 트리거만 담당.
 *
 * <p>cron 은 trash purge(:00)와 겹치지 않게 :30 으로 기본 설정. {@code iso.quarantine.purge-cron} 으로 override.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanIsoQuarantineReaper {

	private final OrphanIsoRecoveryService orphanIsoRecoveryService;

	@Scheduled(cron = "${iso.quarantine.purge-cron:0 30 * * * *}")
	public void reap() {
		int purged = orphanIsoRecoveryService.purgeExpired();
		if (purged > 0) {
			log.info("[orphan-reaper] TTL 만료 격리 {} 건 폐기 완료.", purged);
		}
	}
}
