package com.example.serverprovision.management.common.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * MK3 — restore 검증 (1) 실패 : DB 의 trashed_path 위치에 자원 파일이 부재.
 * <p>외부에서 trash 강제 비우기 등으로 자원이 사라진 상태.
 * 자동 복구 불가 — 사용자가 DB hard-delete 또는 외부 복구 결정 필요.</p>
 */
public class RestoreTrashLostException extends NotFoundException {

    public RestoreTrashLostException(String trashedPath) {
        super("휴지통 자원 부재 — 외부에서 정리된 것으로 보입니다 : " + trashedPath);
    }
}
