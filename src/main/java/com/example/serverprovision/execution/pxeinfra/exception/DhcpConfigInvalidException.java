package com.example.serverprovision.execution.pxeinfra.exception;

import com.example.serverprovision.global.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 렌더된 dhcpd 조각이 {@code dhcpd -t} 문법 검사에서 거절된 경우(REJECTED). 사용자 입력에서 비롯한 잘못이라
 * {@code @ResponseStatus(400)} 으로 매핑되어 {@code WebExceptionHandler.handleDomain} 이 그대로 흡수한다.
 *
 * <p>메시지에 {@code dhcpd -t} 원문을 그대로 담아 사용자가 거절 사유를 직접 확인할 수 있게 한다.</p>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DhcpConfigInvalidException extends DomainException {

    public DhcpConfigInvalidException(String gateOutput) {
        super("dhcpd 구성 검증 실패:\n" + gateOutput);
    }
}
