package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.marker.DriftKind;

/**
 * PATH_DRIFT 외 종류 (MISSING / SIGNATURE_INVALID / HASH_MISMATCH / ORPHAN) 에 자동 적용 호출.
 * 자동 적용 정책 (D13) — PATH_DRIFT 만 안전하게 자동화 가능. → 409
 */
public class DriftAutoApplyNotAllowedException extends ConflictException {

    public DriftAutoApplyNotAllowedException(DriftKind kind) {
        super("자동 적용은 PATH_DRIFT 종류에만 허용됩니다. 현재 종류 : " + kind);
    }
}
