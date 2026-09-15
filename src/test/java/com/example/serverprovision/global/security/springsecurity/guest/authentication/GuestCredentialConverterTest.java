package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.global.security.springsecurity.guest.GuestAuthenticationToken;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** S19-1 D-2 — credential 읽기: 서빙 경로는 경로 토큰(헤더 무시) · 그 밖은 헤더 · 없으면 null · 경로 세그먼트가 UUID 가 아니면 값 null credential. */
class GuestCredentialConverterTest {

	private final GuestCredentialConverter converter = new GuestCredentialConverter();

	private static MockHttpServletRequest request(String method, String uri, String header) {
		MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
		req.setRequestURI(uri);
		if (header != null) {
			req.addHeader(GuestCredentialConverter.TOKEN_HEADER, header);
		}
		return req;
	}

	@Test
	@DisplayName("에이전트 경로 — 헤더 토큰 · 공백은 trim · 헤더 없음 · 빈 헤더는 null")
	void headerToken() {
		Authentication a = converter.convert(request("POST", "/api/pxe/v1/agent/checkin", "  abc123  "));
		assertThat(((GuestAuthenticationToken) a).credential()).isEqualTo(new GuestCredential.HeaderToken("abc123"));
		assertThat(a.isAuthenticated()).isFalse();
		assertThat(converter.convert(request("POST", "/api/pxe/v1/agent/checkin", null))).isNull();
		assertThat(converter.convert(request("POST", "/api/pxe/v1/agent/checkin", "   "))).isNull();
		assertThat(converter.convert(request("GET", "/api/pxe/v1/boot", null))).isNull();
	}

	@Test
	@DisplayName("서빙 경로 — /windows/{token}/… · /firmware/{token}/… 는 경로 토큰이 credential 이고 헤더는 읽지 않는다")
	void servingToken_pathWins() {
		UUID token = UUID.randomUUID();
		Authentication w = converter.convert(request("GET", "/api/pxe/v1/windows/" + token + "/wimboot", "header-ignored"));
		assertThat(((GuestAuthenticationToken) w).credential())
				.isEqualTo(new GuestCredential.ServingToken(token, GuestCredential.ServingKind.WINDOWS));
		Authentication f = converter.convert(request("GET", "/api/pxe/v1/firmware/" + token + "/bios.bin", null));
		assertThat(((GuestAuthenticationToken) f).credential())
				.isEqualTo(new GuestCredential.ServingToken(token, GuestCredential.ServingKind.FIRMWARE));
	}

	@Test
	@DisplayName("서빙 경로의 세그먼트가 UUID 가 아니면 값이 null 인 credential — provider 가 401 로 끝낸다(경로 조작 · 위조)")
	void servingToken_malformed() {
		Authentication a = converter.convert(request("GET", "/api/pxe/v1/windows/not-a-uuid/wimboot", null));
		assertThat(((GuestAuthenticationToken) a).credential())
				.isEqualTo(new GuestCredential.ServingToken(null, GuestCredential.ServingKind.WINDOWS));
	}
}
