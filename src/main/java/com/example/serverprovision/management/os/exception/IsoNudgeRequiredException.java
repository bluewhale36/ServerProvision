package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;

/**
 * MK2 — ISO 업로드 후처리에서 해시 충돌이 발견되었고, 충돌 후보가 모두 soft-deleted 또는 deprecated 인
 * 경우. 사용자 confirm 으로 진행 여부를 결정해야 한다.
 *
 * <p>{@link NudgeRequiredResponse} payload 를 동봉해 컨트롤러가 그대로 응답 body 로 사용한다.
 * Active ISO 와의 해시 충돌은 본 예외가 아닌 {@link DuplicateISOContentException} 로 fail-fast.</p>
 */
public class IsoNudgeRequiredException extends ConflictException {

    private final NudgeRequiredResponse payload;

    public IsoNudgeRequiredException(NudgeRequiredResponse payload) {
        super("동일한 해시의 자원이 휴지통/Deprecated 에 존재합니다. (nudgeId=" + payload.nudgeId() + ")");
        this.payload = payload;
    }

    public NudgeRequiredResponse payload() {
        return payload;
    }
}
