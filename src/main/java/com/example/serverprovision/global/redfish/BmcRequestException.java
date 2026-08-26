package com.example.serverprovision.global.redfish;

/**
 * BMC 접속 실패 신호의 공통 상위(E3-2 D-4) — Redfish 와 AMI 웹 API 는 인증 모델이 다르지만 "자격증명이
 * 거부됐는가" 라는 물음은 같다. {@link BmcCredentialsFallback} 이 이 물음 하나로 다음 후보를 결정하므로
 * 프로토콜이 늘어도 폴백 루프에 분기가 자라지 않는다. 컨트롤러까지 새지 않는 내부 전용 계층이다.
 */
public abstract class BmcRequestException extends RuntimeException {

    protected BmcRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 자격증명 거부 — 폴백이 다음 후보로 넘어가는 유일한 조건. */
    public abstract boolean authFailure();
}
