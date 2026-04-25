package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 이전 스캔이 아직 RUNNING 상태인데 새 스캔 트리거 시도. 동시 스캔 금지. → 409
 */
public class ReconciliationAlreadyRunningException extends ConflictException {

    public ReconciliationAlreadyRunningException() {
        super("이전 스캔이 아직 진행 중입니다. 작업 조회 아이콘에서 진행 상태를 확인하세요.");
    }
}
