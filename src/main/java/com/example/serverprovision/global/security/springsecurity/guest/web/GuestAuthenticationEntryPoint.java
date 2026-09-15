package com.example.serverprovision.global.security.springsecurity.guest.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** credential 없음(entry point)과 credential 틀림(필터 실패)이 같은 401 JSON 이다(S19-1 D-4 · D-5). */
@Component
@RequiredArgsConstructor
public class GuestAuthenticationEntryPoint implements AuthenticationEntryPoint, AuthenticationFailureHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		GuestJsonResponses.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
				GuestJsonResponses.UNAUTHENTICATED_MESSAGE, GuestJsonResponses.UNAUTHENTICATED_CODE);
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		commence(request, response, exception);
	}
}
