package com.example.serverprovision.global.job.controller;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.dto.response.BackgroundJobListResponse;
import com.example.serverprovision.global.job.dto.response.BackgroundJobResponse;
import com.example.serverprovision.global.job.exception.BackgroundJobNotFoundException;
import com.example.serverprovision.global.job.service.BackgroundJobService;
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
	public BackgroundJobListResponse list() {
		List<BackgroundJobResponse> jobs = backgroundJobService.snapshot().stream()
				.map(BackgroundJobResponse::from)
				.toList();
		return new BackgroundJobListResponse(jobs);
	}

	@GetMapping("/{id}")
	public BackgroundJobResponse get(@PathVariable("id") String id) {
		BackgroundJob job = backgroundJobService.find(id)
				.orElseThrow(() -> new BackgroundJobNotFoundException(id));
		return BackgroundJobResponse.from(job);
	}

	@PostMapping("/{id}/dismiss")
	public ResponseEntity<Void> dismiss(@PathVariable("id") String id) {
		backgroundJobService.dismiss(id);
		return ResponseEntity.noContent().build();
	}
}
