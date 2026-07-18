package com.example.serverprovision.execution.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 개시할 수 없는 서버에 대한 개시 요청(E1-0a, DEC-26). 정상 흐름은 UI 가 버튼을 숨겨 차단하므로
 * direct POST · stale 화면에서만 도달하는 안전망이다. (advice 가 base {@link ConflictException} 으로 409 매핑)
 * 사유 2종은 메시지로 구분한다 — 사유별 클래스 분리는 소비 분기가 생기는 시점에(현재는 표시만).
 */
public class ProvisioningStartRejectedException extends ConflictException {

    private ProvisioningStartRejectedException(String message) {
        super(message);
    }

    public static ProvisioningStartRejectedException alreadyStarted(UUID id) {
        return new ProvisioningStartRejectedException("이미 개시된 서버입니다. id=" + id);
    }

    public static ProvisioningStartRejectedException decommissioned(UUID id) {
        return new ProvisioningStartRejectedException("회수된 서버는 프로비저닝을 개시할 수 없습니다. id=" + id);
    }
}
