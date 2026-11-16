package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.exception.ErrorReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

/** HF29 — 거절 결정이 ReasonedDenial 이면 사유를 요청 속성에 두고, 응답은 기본 처리(sendError 403)에 그대로 맡긴다. */
class ReasonedAccessDeniedHandlerTest {

	private final ReasonedAccessDeniedHandler handler = new ReasonedAccessDeniedHandler();

	@Test
	@DisplayName("ReasonedDenial — 요청 속성에 사유 · 응답 403(기본 처리 위임)")
	void reasonedDenial_setsAttribute() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		handler.handle(request, response, new AuthorizationDeniedException("Access Denied", new ReasonedDenial("사유 문장")));
		assertThat(request.getAttribute(ErrorReason.REQUEST_ATTRIBUTE)).isEqualTo("사유 문장");
		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	@DisplayName("사유 없는 거절(일반 AuthorizationDecision · 다른 AccessDeniedException) — 속성 없음 · 응답 403")
	void plainDenial_noAttribute() throws Exception {
		for (AccessDeniedException ex : new AccessDeniedException[]{
				new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)),
				new AccessDeniedException("csrf")}) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			MockHttpServletResponse response = new MockHttpServletResponse();
			handler.handle(request, response, ex);
			assertThat(request.getAttribute(ErrorReason.REQUEST_ATTRIBUTE)).isNull();
			assertThat(response.getStatus()).isEqualTo(403);
		}
	}
}
