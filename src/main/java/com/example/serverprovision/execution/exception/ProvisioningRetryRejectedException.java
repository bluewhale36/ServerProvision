package com.example.serverprovision.execution.exception;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 재시도할 수 없는 서버에 대한 재시도 요청(E1-2, DEC-4). 정상 흐름은 UI 가 버튼을 숨기거나
 * disabled + tooltip 으로 차단하므로 direct POST · stale 화면에서만 도달하는 안전망이다
 * (advice 가 base {@link ConflictException} 으로 409 매핑).
 */
public class ProvisioningRetryRejectedException extends ConflictException {

    private ProvisioningRetryRejectedException(String message) {
        super(message);
    }

    public static ProvisioningRetryRejectedException notFailed(UUID id) {
        return new ProvisioningRetryRejectedException("실패 상태가 아닌 서버는 재시도할 수 없습니다. id=" + id);
    }

    /** 펌웨어 flash 실패의 재시도 차단 — 원인 미상 재-flash 는 벽돌 리스크(DEC-4). */
    public static ProvisioningRetryRejectedException firmwareBlocked(UUID id, ProvisioningPhaseStep step) {
        return new ProvisioningRetryRejectedException(
                "펌웨어 flash 실패(" + step + ")는 재시도가 차단됩니다 — 원인 확인 전 재-flash 는 벽돌 리스크. id=" + id);
    }
}
