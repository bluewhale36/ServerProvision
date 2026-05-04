package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

/**
 * MK2 — ISO 업로드 후처리에서 해시 충돌이 발견되었고, 충돌 후보가 모두 soft-deleted 또는 deprecated 인
 * 경우. 사용자 confirm 으로 진행 여부를 결정해야 한다.
 *
 * <p>WAVE 1 리팩터 — {@link NudgeRequiredException} 추상 super 상속. advice 는 super 매칭만으로
 * 본 sub-class 도 자동 매핑된다.</p>
 */
public class IsoNudgeRequiredException extends NudgeRequiredException {

    public IsoNudgeRequiredException(NudgeRequiredResponse payload) {
        super("동일한 해시의 자원이 휴지통/Deprecated 에 존재합니다. (nudgeId=" + payload.nudgeId() + ")", payload);
    }

    /** 단계 A (intent path 충돌) 생성자. */
    public IsoNudgeRequiredException(String message, NudgeRequiredResponse payload) {
        super(message, payload);
    }
}
