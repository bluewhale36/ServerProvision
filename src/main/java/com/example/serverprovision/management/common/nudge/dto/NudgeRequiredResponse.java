package com.example.serverprovision.management.common.nudge.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MK2 — 업로드 단계 B (해시 검증 후) 충돌 발견 시 응답 body. 결정 #2 (CP1 v3) — 409 status 와 함께 반환.
 *
 * <p>클라이언트는 본 body 의 {@code nudgeId} 를 modal 에 보관하고, 사용자 3택 (proceed / replace / cancel)
 * 중 하나를 선택해 confirm 엔드포인트를 호출한다.</p>
 */
public record NudgeRequiredResponse(
		String code,
		// "NUDGE_REQUIRED" 고정
		UUID nudgeId,
		List<NudgeConflictEntry> conflicts,
		Instant expiresAt
) {

	public static NudgeRequiredResponse of(
			UUID nudgeId,
			List<NudgeConflictEntry> conflicts,
			Instant expiresAt
	) {
		return new NudgeRequiredResponse("NUDGE_REQUIRED", nudgeId, conflicts, expiresAt);
	}
}
