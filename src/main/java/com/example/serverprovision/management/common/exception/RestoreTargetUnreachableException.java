package com.example.serverprovision.management.common.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3 — restore 검증 (2) 실패 : DB 의 원래 경로 (iso_path) 의 부모 디렉토리에 접근 불가.
 * <p>예: 부모 디렉토리가 외부에서 삭제되었거나 권한 변경.
 * 사용자 결정 필요 — 부모 디렉토리 생성 후 재시도, 또는 다른 경로로 restore.</p>
 */
public class RestoreTargetUnreachableException extends ConflictException {

    public RestoreTargetUnreachableException(String parentPath) {
        super("복원 대상 부모 디렉토리에 접근할 수 없습니다 : " + parentPath);
    }
}
