package com.example.serverprovision.global.redfish;

import lombok.Getter;

/**
 * {@code RedfishClient} 내부 전용 실패 신호 — 컨트롤러까지 새지 않는다. {@code RedfishPowerService} 가
 * 전부 {@code PowerControlResult} 로 변환하므로 advice 매핑 대상이 아니다(E1.5 §7 — 신규 공개 예외 0).
 */
@Getter
public class RedfishRequestException extends BmcRequestException {

    private final RedfishError error;

    public RedfishRequestException(RedfishError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    @Override
    public boolean authFailure() {
        return error == RedfishError.AUTH_FAILED;
    }
}
