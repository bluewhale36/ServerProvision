package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MK2 — BIOS 업로드 단계 B (해시 검증 후) 에서 SoftDeleted / Deprecated 자원과 해시가 충돌하는 경우.
 *
 * <p>{@link ConflictException} 을 상속하여 advice 가 자동으로 409 로 매핑하되, body 포맷이 일반
 * {@code ApiErrorResponse} 가 아니라 {@code NudgeRequiredResponse} 이므로 advice 측에서 본 sub-class
 * 핸들러를 별도 정의해 nudge 메타 (nudgeId · conflicts · expiresAt) 을 노출한다.</p>
 *
 * <p>BiosService 가 {@link com.example.serverprovision.management.common.nudge.NudgeRegistry#register} 로
 * 세션을 만들고 본 예외를 throw 하면 컨트롤러는 try/catch 없이 advice 만으로 정확한 응답을 회신한다 —
 * 컨트롤러 try/catch 추가 절대 금지 원칙 정합.</p>
 */
public class BiosNudgeRequiredException extends ConflictException {

    private final UUID nudgeId;
    private final List<NudgeConflictEntry> conflicts;
    private final Instant expiresAt;

    public BiosNudgeRequiredException(NudgeSession session, List<NudgeConflictEntry> conflicts) {
        super("동일한 해시의 자원이 이미 존재합니다. nudge 결정이 필요합니다.");
        this.nudgeId = session.nudgeId();
        this.conflicts = conflicts;
        this.expiresAt = session.expiresAt();
    }

    public UUID nudgeId() {
        return nudgeId;
    }

    public List<NudgeConflictEntry> conflicts() {
        return conflicts;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
