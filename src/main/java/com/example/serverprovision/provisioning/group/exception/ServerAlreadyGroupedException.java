package com.example.serverprovision.provisioning.group.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 이미 다른 그룹에 속한 서버를 넣으려 할 때 (U3-4).
 *
 * <p>한 서버는 최대 한 그룹에만 속한다(DEC-B). 화면은 무소속 서버만 후보로 보여주므로
 * 정상 흐름에서는 도달하지 않고, direct POST 나 다른 창에서 먼저 편입된 뒤의 stale 제출에서만 발동한다.</p>
 *
 * <p>메시지는 {@code GuestServerGroup.addBlockReason(...)} 이 만든 문자열을 그대로 받는다 —
 * 화면 tooltip 과 서버 거절 사유가 한 메서드에서 나오므로 둘이 어긋날 수 없다.</p>
 */
public class ServerAlreadyGroupedException extends ConflictException {

    public ServerAlreadyGroupedException(String blockReason) {
        super(blockReason);
    }
}
