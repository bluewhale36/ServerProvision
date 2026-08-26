package com.example.serverprovision.execution.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 회수되지 않은 서버의 영구 삭제 시도 거절(U6 D-5). 정상 흐름은 UI 가 삭제 섹션을 회수 상태에서만
 * 렌더하므로 direct POST 안전망이다. 판정 SSOT 는 {@code GuestServer.purgeBlockReason}.
 */
public class GuestServerNotDecommissionedException extends ConflictException {

    public GuestServerNotDecommissionedException(UUID id) {
        super("회수된 서버만 영구 삭제할 수 있습니다 — 먼저 서버를 회수하십시오. id=" + id);
    }
}
