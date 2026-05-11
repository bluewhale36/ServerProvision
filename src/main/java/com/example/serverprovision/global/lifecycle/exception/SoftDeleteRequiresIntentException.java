package com.example.serverprovision.global.lifecycle.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.lifecycle.DeleteIntent;

/**
 * MK3-2 (DCM3-2.1) — softDelete 진입 시 사전조건 (`Files.exists(DB.path)`) 위반 시 throw.
 *
 * <p>본 예외는 ApiExceptionHandler 가 409 + structured payload (DeleteRejectResponse) 로 매핑한다.
 * payload 에 발급된 intent token 이 동봉되어 클라이언트 modal 에서 두 번째 호출에 사용.</p>
 *
 * <p>본 예외 발생 시점에 DeleteIntentRegistry 에 이미 intent 가 등록된 상태 — service 가 issue 후 throw.</p>
 */
public class SoftDeleteRequiresIntentException extends ConflictException {

    private final DeleteIntent intent;

    public SoftDeleteRequiresIntentException(DeleteIntent intent) {
        super("softDelete 진입 시 자원이 추적 위치에 없습니다 — 사용자 명시 액션 필요. type="
                + intent.resourceType() + " id=" + intent.resourceId()
                + " missingPath=" + intent.missingPath());
        this.intent = intent;
    }

    public DeleteIntent intent() {
        return intent;
    }
}
