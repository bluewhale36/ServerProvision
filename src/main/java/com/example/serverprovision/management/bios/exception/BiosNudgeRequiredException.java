package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MK2 — BIOS nudge 필요 상태.
 *
 * <p>두 단계에서 발생 :
 * <ul>
 *   <li>단계 A (intent / WAVE 2) — (board, version) 메타가 같은 SoftDeleted/Deprecated 자원 발견.
 *       파일 업로드 자체를 막고 사용자에게 사전 결정을 받음.</li>
 *   <li>단계 B (hash / WAVE 1 이전) — 업로드 후 해시 충돌. 임시 트리는 보관된 채 사용자 결정 대기.</li>
 * </ul>
 *
 * <p>{@link NudgeRequiredException} 추상 super 상속. advice 는 super 매칭만으로 본 sub-class 도 자동
 * 매핑된다. 메시지만 phase 별로 다르고, payload (NudgeRequiredResponse) 형식은 동일.</p>
 */
public class BiosNudgeRequiredException extends NudgeRequiredException {

    /** 단계 B (해시 충돌) 생성자. */
    public BiosNudgeRequiredException(NudgeSession session, List<NudgeConflictEntry> conflicts) {
        super("동일한 해시의 자원이 이미 존재합니다. nudge 결정이 필요합니다.",
                NudgeRequiredResponse.of(session.nudgeId(), conflicts, session.expiresAt()));
    }

    /** 단계 A (intent 메타 충돌) 생성자. */
    public BiosNudgeRequiredException(String message, NudgeSession session, List<NudgeConflictEntry> conflicts) {
        super(message,
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
