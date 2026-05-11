package com.example.serverprovision.management.common.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * MK3-2 (DCM3-2.4) — saga {@code reconcileThenDelete} 의 단계 (2) PATH_DRIFT 미발견 또는
 * 자동 재시도 3회 모두 실패 시 throw.
 *
 * <p>ApiExceptionHandler 가 422 Unprocessable Entity 로 매핑. 클라이언트는 사용자에게
 * fallback (FORCED_CLEAR) 옵션 안내.</p>
 */
public class PathCorrectionFailedException extends DomainException {

    public PathCorrectionFailedException(String message) {
        super(message);
    }
}
