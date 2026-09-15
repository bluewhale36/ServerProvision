package com.example.serverprovision.global.security.springsecurity.guest.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 첫 접촉({@code /boot} · {@code /assets/**})의 거절(S19-2 D-5) — 401 + {@code WWW-Authenticate: Basic} + 사람이 읽는 한 줄.
 * iPXE 는 401 을 실행하지 않고 콘솔에 "Permission denied" 를 내므로, 본문은 boot.ipxe 의 credential 을 고칠 운영자를 위한 단서다.
 * 게스트 체인의 httpBasic 실패(틀린 secret)도 이 entry point 로 끝난다.
 */
@Component
public class PxeBootEntryPoint implements AuthenticationEntryPoint {

	static final String REALM = "provision-pxe";
	static final String BODY = "PXE 부팅 credential 이 필요합니다. boot.ipxe 의 chain URL 에 http://<user>:<secret>@<서버>/api/pxe/v1/boot?... 로 credential 을 넣습니다 (pxe.boot.secret).\n";

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + REALM + "\"");
		response.setContentType(MediaType.TEXT_PLAIN_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(BODY);
	}
}
