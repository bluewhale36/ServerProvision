package com.example.serverprovision.global.trash.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * MK3 — Trash 이동 또는 복원 도중 IOException 발생 후 saga 보상 retry 도 실패한 critical 상황.
 * <p>예: 디스크 풀, 권한 변경, 파일시스템 손상 등. 운영자 즉시 인지 필요.
 * 이 예외 도달 시 DB 는 이전 상태로 롤백된 상태이며, 디스크는 부분 변경 상태일 수 있어 운영자 수동 정리 권고.</p>
 */
public class TrashMoveFailedException extends DomainException {

    public TrashMoveFailedException(String message) {
        super(message);
    }

    public TrashMoveFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
