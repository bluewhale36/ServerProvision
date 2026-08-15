package com.example.serverprovision.provisioning.assignment.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 서버 자신의 상태 때문에 세팅 정의서를 붙일 수 없다 (U3-5-a) — 현재는 회수된 서버.
 *
 * <p>사유 문자열은 {@code GuestServer.assignBlockReason()} 이 만든 것을 그대로 싣는다. 화면이 폼을 닫으며
 * 보여주는 문구와 이 예외의 메시지가 같은 곳에서 나오므로 어긋날 수 없다.</p>
 *
 * <p>{@link DefinitionHardwareMismatchException} 과 나눠 둔 이유는 <b>운영자가 취할 다음 행동이 다르기</b>
 * 때문이다 — 이쪽은 서버를 다시 등록해야 하고, 저쪽은 맞는 정의서를 고르거나 맞는 서버를 골라야 한다.</p>
 *
 * <p>advice 에 명시 등록하지 않는다. 상위 타입 핸들러가 {@code @ResponseStatus(409)} 를 계층 탐색으로
 * 읽어 흡수한다.</p>
 */
public class ServerNotAssignableException extends ConflictException {

    private final UUID guestServerId;

    public ServerNotAssignableException(UUID guestServerId, String reason) {
        super(reason);
        this.guestServerId = guestServerId;
    }

    public UUID getGuestServerId() {
        return guestServerId;
    }
}
