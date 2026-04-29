package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.NotFoundException;

import java.util.UUID;

/**
 * MK2 — nudge_session 부재 (잘못된 nudgeId / 이미 resolve 됨).
 */
public class NudgeNotFoundException extends NotFoundException {
    public NudgeNotFoundException(UUID nudgeId) {
        super("존재하지 않는 nudgeId 입니다 : " + nudgeId);
    }
}
