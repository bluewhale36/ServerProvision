package com.example.serverprovision.global.bmcweb;

import com.example.serverprovision.global.redfish.BmcRequestException;
import lombok.Getter;

/**
 * AMI 웹 API 호출 실패 — {@code RedfishRequestException} 과 같은 내부 전용 신호(advice 미매핑).
 * {@link #authFailure()} 로 {@code BmcCredentialsFallback} 의 사다리를 Redfish 와 공유한다(E3-2 D-4).
 */
@Getter
public class AmiWebRequestException extends BmcRequestException {

    private final AmiWebError error;
    /** BMC 가 준 오류 코드(데이터 거절의 {@code code}) — 없으면 null. */
    private final Integer code;

    public AmiWebRequestException(AmiWebError error, String message, Integer code, Throwable cause) {
        super(message, cause);
        this.error = error;
        this.code = code;
    }

    @Override
    public boolean authFailure() {
        return error == AmiWebError.AUTH_FAILED;
    }
}
