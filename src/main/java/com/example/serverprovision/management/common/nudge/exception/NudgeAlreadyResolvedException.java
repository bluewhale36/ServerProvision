package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * MK2 — 같은 nudgeId 에 두 번째 confirm 호출. 멱등 보장 X — 명시적 충돌.
 */
public class NudgeAlreadyResolvedException extends ConflictException {
    public NudgeAlreadyResolvedException(UUID nudgeId) {
        super("이미 처리된 nudge 세션입니다. (nudgeId=" + nudgeId + ")");
    }
}
