package com.example.serverprovision.provisioning.assignment.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 개시된(ACTIVE_CONSUMED) 활성 할당의 재할당 차단(U3-2-a) — 개시 후 정의서 교체는 진행 커서 리셋 / 재개
 * 의미론(E cluster)을 요구하므로 U3-2-a 경계 밖이다. 정상 흐름은 UI 가 재할당 버튼을 {@code disabled} + tooltip
 * 으로 1차 차단하고(같은 SSOT {@code SettingAssignmentSnapshot.reassignBlockReason()}), 이 예외는 direct POST · 동시성 ·
 * stale 을 잡는 안전망(409)이다. (advice 가 base {@link ConflictException} 으로 409 매핑.)
 *
 * <p>메시지는 {@code reassignBlockReason()}(도메인 메서드 SSOT)이 반환한 사유를 그대로 싣는다 — UI tooltip 과
 * 서버 예외 메시지가 갈라지지 않게 한다.</p>
 */
public class ReassignAfterStartException extends ConflictException {

    public ReassignAfterStartException(UUID guestId, String reason) {
        super(reason + " guestId=" + guestId);
    }
}
