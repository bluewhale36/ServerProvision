package com.example.serverprovision.maintenance.trash.controller;

import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.global.trash.service.PurgeLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;

/**
 * S5-2-4 — 운영자 수동 재시도 진입점. 자원의 마지막 outcome=FAILED 인 자원만 1회 강제 PurgeExecutor
 * 재진입. TTL_AUTO 의 자연 cron 폴링 외에 즉시 강제 재시도가 필요할 때 사용.
 *
 * <p>관리자 권한 — S3 인증 통합 시점에 권한 가드 추가.</p>
 */
@Slf4j
@Controller
@RequestMapping("/maintenance/trash/retry-failed")
@RequiredArgsConstructor
public class TrashRetryController {

	private final PurgeLogService purgeLogService;
	private final PurgeExecutor purgeExecutor;

	@PostMapping
	public String retryFailed() {
		Set<ResourceKey> candidates = purgeLogService.findResourcesWithLastOutcomeFailed();
		log.info("[trash-retry] 운영자 수동 재시도 시작. candidates={}", candidates.size());
		int success = 0;
		int failed = 0;
		for (ResourceKey key : candidates) {
			try {
				var result = purgeExecutor.execute(
						PurgeRequest.forTtlAuto(key.resourceType(), key.resourceId()));
				if (result instanceof com.example.serverprovision.global.trash.PurgeResult.Success) success++;
				else failed++;
			} catch (Exception ex) {
				failed++;
				log.error(
						"[trash-retry] PurgeExecutor 호출 실패. type={} id={} cause={}",
						key.resourceType(), key.resourceId(), ex.toString(), ex
				);
			}
		}
		log.info("[trash-retry] 완료. success={} failed={}", success, failed);
		return "redirect:/maintenance/trash/purge-log";
	}
}
