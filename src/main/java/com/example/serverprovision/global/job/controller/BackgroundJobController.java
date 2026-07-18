package com.example.serverprovision.global.job.controller;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.dto.response.BackgroundJobListResponse;
import com.example.serverprovision.global.job.dto.response.BackgroundJobResponse;
import com.example.serverprovision.global.job.exception.BackgroundJobNotFoundException;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.web.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 알림 센터 공용 REST.
 * <ul>
 *   <li>{@code GET  /jobs} — 알림 패널이 폴링하는 스냅샷</li>
 *   <li>{@code GET  /jobs/{id}} — 단건 세밀 폴링</li>
 *   <li>{@code POST /jobs/{id}/dismiss} — 종료된 Job 카드 제거</li>
 * </ul>
 * ISO 업로드 progress 보고 엔드포인트는 업로드를 foreground XHR (옵션 A) 로 전환하면서 제거했다.
 * 업로드는 페이지 내 진행 바에서 실시간으로 보여주는 것이 사용자 의도 (지켜보기 패러다임) 다.
 */
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class BackgroundJobController {

	private final BackgroundJobService backgroundJobService;

	@GetMapping
	public BackgroundJobListResponse list(HttpServletRequest request) {
		List<BackgroundJob> snapshot = backgroundJobService.snapshot();
		// 진행 중인 Job 이 하나도 없으면 이 폴링 응답은 로그로 남기지 않는다 — 활성 작업이 있을 때만 남긴다.
		boolean anyActive = snapshot.stream().anyMatch(job -> !job.getStatus().isTerminal());
		if (!anyActive) {
			request.setAttribute(RequestCorrelationFilter.SUPPRESS_ACCESS_LINE, true);
		}
		return new BackgroundJobListResponse(snapshot.stream().map(BackgroundJobResponse::from).toList());
	}

	@GetMapping("/{id}")
	public BackgroundJobResponse get(@PathVariable("id") String id, HttpServletRequest request) {
		BackgroundJob job = backgroundJobService.find(id)
				.orElseThrow(() -> new BackgroundJobNotFoundException(id));
		// 단건 세밀 폴링도 대상 Job 이 종료됐으면 로그를 남기지 않는다 (완료/실패 1회 로그는 서비스가 담당).
		if (job.getStatus().isTerminal()) {
			request.setAttribute(RequestCorrelationFilter.SUPPRESS_ACCESS_LINE, true);
		}
		return BackgroundJobResponse.from(job);
	}

	@PostMapping("/{id}/dismiss")
	public ResponseEntity<Void> dismiss(@PathVariable("id") String id) {
		backgroundJobService.dismiss(id);
		return ResponseEntity.noContent().build();
	}
}
