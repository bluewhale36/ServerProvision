package com.example.serverprovision.global.systemsetting.exception;

import com.example.serverprovision.global.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 최소 권한이 모자란 사용자의 설정 저장(direct POST · 권한이 바뀐 뒤의 stale 화면). 정상 화면에서는 그 설정이 보이지 않는다.
 *
 * <p>보안 계층({@code SecurityException})이 아니라 도메인 403 으로 둔다. 이 저장소의 보안 예외는 항상 JSON 으로만 응답하는데
 * (WebExceptionHandler 주석 · R15-1), 이 예외는 네이티브 폼 제출에서도 닿는다. 도메인 예외는 HTML 이면 오류 화면, JSON 이면
 * JSON 으로 같은 403 을 낸다(U7 CP5 F-1).</p>
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class SystemSettingAccessDeniedException extends DomainException {

    public SystemSettingAccessDeniedException() {
        super("이 설정을 변경할 권한이 없습니다.");
    }
}
