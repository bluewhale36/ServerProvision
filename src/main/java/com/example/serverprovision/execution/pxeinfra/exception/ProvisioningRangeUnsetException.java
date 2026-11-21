package com.example.serverprovision.execution.pxeinfra.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 프로비저닝 허용 대역이 없어 dnsmasq 서브넷을 정할 수 없다(HF35). 화면은 저장 버튼을 막고 같은 사유를 보이므로,
 * 이 예외는 direct POST 나 대역을 지운 직후의 stale 화면에서만 난다.
 */
public class ProvisioningRangeUnsetException extends ConflictException {

    public static final String MESSAGE = "프로비저닝 허용 대역을 먼저 시스템 설정에서 저장해야 합니다.";

    public ProvisioningRangeUnsetException() {
        super(MESSAGE);
    }
}
