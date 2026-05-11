package com.example.serverprovision.global.trash.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3-1 — clear-ghost 액션이 ghost 가 아닌 row (active 또는 정상 trash) 에 호출됐을 때의 방어 가드.
 * <p>UI 가 ghost 행에만 정리 버튼을 노출하지만, race / 직접 endpoint 호출 등으로 비-ghost 자원에 도달
 * 가능. 사고 방지를 위해 명시적 거절.</p>
 */
public class GhostClearTargetNotGhostException extends ConflictException {

    public GhostClearTargetNotGhostException(String resourceTypeAndId) {
        super("ghost 가 아닌 자원에는 정리 액션을 사용할 수 없습니다 — "
                + resourceTypeAndId
                + " · 활성 자원은 도메인 페이지에서, soft-deleted 자원은 휴지통에서 영구삭제하세요.");
    }
}
