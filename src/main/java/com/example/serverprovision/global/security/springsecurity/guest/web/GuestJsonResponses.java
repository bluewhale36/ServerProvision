package com.example.serverprovision.global.security.springsecurity.guest.web;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 게스트 체인의 거절 응답 — 로그인 리다이렉트가 아니라 {@link ApiErrorResponse} JSON 이다(에이전트 채널의 오류 형식과 같다). */
final class GuestJsonResponses {

	static final String UNAUTHENTICATED_MESSAGE = "게스트 인증이 필요합니다.";
	static final String UNAUTHENTICATED_CODE = "GUEST_UNAUTHENTICATED";
	static final String FORBIDDEN_MESSAGE = "게스트 credential 로는 허용되지 않는 요청입니다.";
	static final String FORBIDDEN_CODE = "GUEST_FORBIDDEN";
	static final String LAN_FORBIDDEN_MESSAGE = "프로비저닝 LAN 밖에서는 허용되지 않는 요청입니다.";
	static final String LAN_FORBIDDEN_CODE = "PXE_LAN_FORBIDDEN";

	private GuestJsonResponses() {
	}

	static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String message, String code)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(objectMapper.writeValueAsString(new ApiErrorResponse(message, null, code)));
	}
}
