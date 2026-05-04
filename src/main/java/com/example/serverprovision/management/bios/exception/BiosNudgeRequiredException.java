package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MK2 — BIOS 업로드 단계 B (해시 검증 후) 에서 SoftDeleted / Deprecated 자원과 해시가 충돌하는 경우.
 *
 * <p>WAVE 1 리팩터 — {@link NudgeRequiredException} 추상 super 상속. advice 는 super 매칭만으로
 * 본 sub-class 도 자동 매핑된다. 기존 {@code nudgeId/conflicts/expiresAt} 접근자는 backward-compat
 * 으로 유지.</p>
 */
public class BiosNudgeRequiredException extends NudgeRequiredException {

    public BiosNudgeRequiredException(NudgeSession session, List<NudgeConflictEntry> conflicts) {
        super("동일한 해시의 자원이 이미 존재합니다. nudge 결정이 필요합니다.",
                NudgeRequiredResponse.of(session.nudgeId(), conflicts, session.expiresAt()));
    }

    public UUID nudgeId() {
        return payload().nudgeId();
    }

    public List<NudgeConflictEntry> conflicts() {
        return payload().conflicts();
    }

    public Instant expiresAt() {
        return payload().expiresAt();
    }
}
