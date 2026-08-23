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

    /**
     * 펌웨어를 쓰는 도중 실패한 서버의 재시도 차단(DEC-4) — 원인을 모른 채 다시 쓰면 장비가 부팅하지
     * 못하는 상태가 될 수 있다. 사용자 문구에는 내부 결정 번호 · 영문 용어를 싣지 않는다(E2-1-b CP5 F-2).
     */
    public static ProvisioningRetryRejectedException firmwareBlocked(UUID id, ProvisioningPhaseStep step) {
        return new ProvisioningRetryRejectedException(
                "펌웨어를 쓰는 도중 실패한 서버는 원인을 확인하기 전에 다시 시도할 수 없습니다. id=" + id);
    }
}
