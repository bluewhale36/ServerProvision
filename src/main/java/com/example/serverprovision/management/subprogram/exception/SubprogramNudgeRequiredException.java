package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

import java.util.List;

/**
 * MK2 — Subprogram (Driver / Utility) nudge 필요 상태.
 *
 * <p>두 단계에서 발생 :
 * <ul>
 *   <li>단계 A (intent / WAVE 2) — (kind, scope, name, version) 메타가 같은 SoftDeleted/Deprecated 자원 발견.</li>
 *   <li>단계 B (hash) — 업로드 후 해시 충돌. 임시 트리는 보관된 채 사용자 결정 대기.</li>
 * </ul>
 */
public class SubprogramNudgeRequiredException extends NudgeRequiredException {

	public SubprogramNudgeRequiredException(NudgeSession session, List<NudgeConflictEntry> conflicts) {
		super(
				"동일한 해시의 Subprogram 자원이 이미 존재합니다. nudge 결정이 필요합니다.",
				NudgeRequiredResponse.of(session.nudgeId(), conflicts, session.expiresAt())
		);
	}

	public SubprogramNudgeRequiredException(String message, NudgeSession session, List<NudgeConflictEntry> conflicts) {
		super(
				message,
				NudgeRequiredResponse.of(session.nudgeId(), conflicts, session.expiresAt())
		);
	}
}
