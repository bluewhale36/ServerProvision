package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.exception.ErrorReason;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 관리 화면(웹) 체인의 403(HF29 · docs C-26). 거절 결정이 {@link ReasonedDenial} 이면 그 사유를 요청 속성에 두고, 응답은 종전 기본
 * 처리({@link AccessDeniedHandlerImpl} → {@code sendError(403)} → {@code /error})에 그대로 맡긴다. 그래서 콘텐츠 협상은 바뀌지 않는다 —
 * 브라우저 요청은 {@code error.html}, XHR · JSON 요청은 JSON 오류 응답이 되고, 둘 다 {@code message} 에 사유가 실린다.
 * 익명 요청은 이 핸들러가 아니라 인증 진입점(로그인 이동)으로 간다(ExceptionTranslationFilter 규칙).
 *
 * <p>빈으로 올려도 안전하다 — AccessDeniedHandler 는 AuthenticationProvider 와 달리 전역 매니저에 끼어들지 않고, 체인이 명시로
 * 등록할 때만 쓰인다. 게스트 체인은 자기 {@code GuestAccessDeniedHandler} 를 쓴다.</p>
 */
@Component
public class ReasonedAccessDeniedHandler implements AccessDeniedHandler {

	private final AccessDeniedHandler delegate = new AccessDeniedHandlerImpl();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws IOException, ServletException {
		if (accessDeniedException instanceof AuthorizationDeniedException denied
				&& denied.getAuthorizationResult() instanceof ReasonedDenial reasoned) {
			request.setAttribute(ErrorReason.REQUEST_ATTRIBUTE, reasoned.reason());
		}
		delegate.handle(request, response, accessDeniedException);
	}
}
