package com.example.serverprovision.global.job;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 무결성 검증 결과({@link IntegrityStatus})를 BackgroundJob 의 stage 전이로 보고한다.
 *
 * <p>R5-2 — 4 verification launcher(BIOS / ISO / BMC / Subprogram)에 글자단위 복제돼 있던 {@code applyStatus}
 * switch 를 단일 SSOT 로 추출. Job stage 부수효과({@link BackgroundJobService} · {@link IntegrityVerificationStage})는
 * 인프라 의존이라 도메인 enum 에 넣지 않고 본 {@code @Component} 가 담당한다(enum 오염 회피).</p>
 *
 * <p>전이 규칙(추출 전 동작 보존) :</p>
 * <ul>
 *   <li>{@code MARKER_MISSING / SIGNATURE_INVALID} : 서명 단계에서 막힘 → 즉시 fail</li>
 *   <li>{@code TAMPERED} : 서명 통과 후 해시 불일치 → 1단계 done, 2단계 진입 후 fail</li>
 *   <li>{@code ORIGINAL} : 두 단계 done → Job complete</li>
 *   <li>{@code NOT_VERIFIED} : 결과 미수신 → 별도 안내 문구로 fail</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class IntegrityJobReporter {

	private final BackgroundJobService backgroundJobService;

	public void report(String jobId, IntegrityStatus status) {
		switch (status) {
			case MARKER_MISSING, SIGNATURE_INVALID -> backgroundJobService.fail(jobId, status.getDisplayMessage());
			case TAMPERED -> {
				backgroundJobService.completeStage(jobId);
				backgroundJobService.startStage(jobId, IntegrityVerificationStage.RECOMPUTE_HASH);
				backgroundJobService.fail(jobId, status.getDisplayMessage());
			}
			case ORIGINAL -> {
				backgroundJobService.completeStage(jobId);
				backgroundJobService.startStage(jobId, IntegrityVerificationStage.RECOMPUTE_HASH);
				backgroundJobService.completeStage(jobId);
				backgroundJobService.complete(jobId);
			}
			case NOT_VERIFIED -> backgroundJobService.fail(jobId, "검증 결과를 받지 못했습니다.");
		}
	}
}
