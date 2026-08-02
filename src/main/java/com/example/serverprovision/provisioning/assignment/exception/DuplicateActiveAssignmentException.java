package com.example.serverprovision.provisioning.assignment.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 활성 유일성 위반 — 게스트당 활성 세팅 정의서 할당은 1개다(결정 D-A). 정상 흐름은 UI 가 재할당을 1차 차단
 * (활성 할당이 있으면 할당 폼 비노출)하고, 이 예외는 direct POST · 동시성 · stale 을 잡는 안전망(409)이다.
 * (advice 가 base {@link ConflictException} 으로 409 매핑. 재할당 UX 는 U3-2.)
 */
public class DuplicateActiveAssignmentException extends ConflictException {

    public DuplicateActiveAssignmentException(UUID guestId) {
        super("이미 활성 세팅 정의서 할당이 있는 게스트입니다. guestId=" + guestId);
    }
}
