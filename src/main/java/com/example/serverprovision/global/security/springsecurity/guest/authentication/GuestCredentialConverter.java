package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.global.security.springsecurity.guest.GuestAuthenticationToken;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * request 에서 게스트 credential 을 읽는다(S19-1 D-2). 서빙 경로({@code /windows/{token}/…} · {@code /firmware/{token}/…})는 경로 토큰이
 * credential 이고 헤더는 읽지 않는다. 그 밖은 {@code X-Guest-Token} 헤더. credential 이 없으면 null — filter 가 건너뛰고 authorization 이 401 로 끝낸다.
 */
public class GuestCredentialConverter implements AuthenticationConverter {

	public static final String TOKEN_HEADER = "X-Guest-Token";
	private static final Pattern SERVING_PATH = Pattern.compile("^/api/pxe/v1/(windows|firmware)/([^/]+)/");

	@Override
	public Authentication convert(HttpServletRequest request) {
		Matcher m = SERVING_PATH.matcher(request.getRequestURI());
		if (m.find()) {
			return GuestAuthenticationToken.unauthenticated(servingToken(m.group(1), m.group(2)));
		}
		String header = request.getHeader(TOKEN_HEADER);
		if (header == null || header.isBlank()) {
			return null;
		}
		return GuestAuthenticationToken.unauthenticated(new GuestCredential.HeaderToken(header.trim()));
	}

	/** 경로 세그먼트가 UUID 가 아니면 위조다 — credential 은 있되 풀리지 않는 값으로 두어 provider 가 401 로 끝내게 한다. */
	private static GuestCredential.ServingToken servingToken(String kind, String raw) {
		GuestCredential.ServingKind servingKind = "windows".equals(kind)
				? GuestCredential.ServingKind.WINDOWS : GuestCredential.ServingKind.FIRMWARE;
		UUID token;
		try {
			token = UUID.fromString(raw);
		} catch (IllegalArgumentException ex) {
			token = null;
		}
		return new GuestCredential.ServingToken(token, servingKind);
	}
}
