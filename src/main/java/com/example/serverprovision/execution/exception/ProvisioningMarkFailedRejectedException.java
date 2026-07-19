package com.example.serverprovision.execution.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 수동 실패 전환이 불가능한 서버에 대한 전환 요청(E1-2, DEC-4 — 무보고 침묵 대응 액션의 안전망).
 * 진행 중(PROVISIONING)이 아닌 서버(미개시 · 실패 · 완주 · 회수)는 UI 가 버튼을 숨긴다 —
 * direct POST 전용 409 (advice 가 base {@link ConflictException} 매핑).
 */
public class ProvisioningMarkFailedRejectedException extends ConflictException {

    private ProvisioningMarkFailedRejectedException(String message) {
        super(message);
    }

    public static ProvisioningMarkFailedRejectedException notProvisioning(UUID id) {
        return new ProvisioningMarkFailedRejectedException(
                "진행 중(PROVISIONING) 상태의 서버만 수동 실패 전환이 가능합니다. id=" + id);
    }
}
