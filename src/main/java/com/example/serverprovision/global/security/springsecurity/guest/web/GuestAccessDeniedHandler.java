package com.example.serverprovision.global.security.springsecurity.guest.web;

import com.example.serverprovision.global.security.springsecurity.guest.authorization.ProvisioningLanPolicy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 인증은 됐으나 허용되지 않는 요청 — 403 JSON. 출발지가 프로비저닝 LAN 밖이면(S19-2 · {@link ProvisioningLanPolicy.Denied})
 * 코드를 {@code PXE_LAN_FORBIDDEN} 으로 갈라 운영자가 credential 문제와 대역 문제를 구분하게 한다.
 */
@Component
@RequiredArgsConstructor
public class GuestAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
			throws IOException {
		if (deniedByLan(accessDeniedException)) {
			GuestJsonResponses.write(response, objectMapper, HttpStatus.FORBIDDEN,
					GuestJsonResponses.LAN_FORBIDDEN_MESSAGE, GuestJsonResponses.LAN_FORBIDDEN_CODE);
			return;
		}
		GuestJsonResponses.write(response, objectMapper, HttpStatus.FORBIDDEN,
				GuestJsonResponses.FORBIDDEN_MESSAGE, GuestJsonResponses.FORBIDDEN_CODE);
	}

	private static boolean deniedByLan(AccessDeniedException ex) {
		return ex instanceof AuthorizationDeniedException denied
				&& denied.getAuthorizationResult() instanceof ProvisioningLanPolicy.Denied;
	}
}
