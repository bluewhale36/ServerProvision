package com.example.serverprovision.execution.pxeinfra.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * dhcpd 서비스 제어(재기동) 실패로 이전본을 복원해 이전 상태로 되돌린 경우(ROLLED_BACK), 또는 문법 게이트
 * 자체가 실행 불능(바이너리 없음·타임아웃)인 경우를 함께 표현한다.
 *
 * <p>{@code @ResponseStatus} 를 붙이지 않아 {@code WebExceptionHandler.handleDomain} 이 500 으로 매핑한다 —
 * 사용자 입력의 잘못이 아니라 서버/인프라 측 문제이기 때문이다.</p>
 */
public class DhcpServiceControlFailedException extends DomainException {

    public DhcpServiceControlFailedException(String detail) {
        super(detail);
    }
}
