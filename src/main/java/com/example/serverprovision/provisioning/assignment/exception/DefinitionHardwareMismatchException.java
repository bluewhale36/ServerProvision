package com.example.serverprovision.provisioning.assignment.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 정의서가 요구하는 하드웨어와 서버가 가진 하드웨어가 맞지 않는다 (U3-5-a).
 *
 * <p><b>하드웨어 축마다 예외를 만들지 않는다.</b> 지금 축은 메인보드 하나뿐이지만 U4 가 디스크 구성
 * 대조를 더할 때 별도 예외를 또 만들면, HTTP 상태도 같고 운영자의 다음 행동도 같은 예외가 축 수만큼
 * 늘어난다. 예외를 나누는 기준은 <b>상태 코드와 운영자의 다음 행동</b>이지 내부 원인이 아니다 —
 * 구체적인 원인은 메시지가 싣고, 그 메시지는 각 축의 판정({@code RequiredBoardModel.blockReasonFor} 등)이
 * 만든다.</p>
 *
 * <p>advice 에 명시 등록하지 않는다. 상위 타입 핸들러가 {@code @ResponseStatus(409)} 를 계층 탐색으로
 * 읽어 흡수한다.</p>
 */
public class DefinitionHardwareMismatchException extends ConflictException {

    private final UUID guestServerId;

    public DefinitionHardwareMismatchException(UUID guestServerId, String reason) {
        super(reason);
        this.guestServerId = guestServerId;
    }

    public UUID getGuestServerId() {
        return guestServerId;
    }
}
