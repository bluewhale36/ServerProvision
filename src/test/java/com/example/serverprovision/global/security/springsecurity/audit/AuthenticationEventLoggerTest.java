package com.example.serverprovision.global.security.springsecurity.audit;

import ch.qos.logback.classic.Level;
import com.example.serverprovision.global.security.springsecurity.guest.GuestAuthenticationToken;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import com.example.serverprovision.global.security.springsecurity.guest.authorization.ProvisioningLanPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S20 — 이벤트 한 종류에 로그 한 줄 · 태그 · 수준 · 비밀값 부재. 출발지와 채널은 현재 요청(RequestContextHolder)에서 읽으므로
 * 요청을 흉내 내 건다.
 */
class AuthenticationEventLoggerTest {

	private static final String SECRET_TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";

	private final AuthenticationEventLogger logger = new AuthenticationEventLogger();
	private AuthLogCapture capture;

	@BeforeEach
	void setUp() {
		capture = new AuthLogCapture();
	}

	@AfterEach
	void tearDown() {
		capture.close();
		RequestContextHolder.resetRequestAttributes();
	}

	private static MockHttpServletRequest request(String uri, String ip) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		request.setRemoteAddr(ip);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		return request;
	}

	private static Authentication webUser(String ip) {
		UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
				"hong.gildong", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(ip);
		auth.setDetails(new WebAuthenticationDetails(request));
		return auth;
	}

	@Test
	@DisplayName("폼 로그인 성공 — INFO [auth.login] 사용자 · 역할 · 채널 web · details 의 출발지")
	void interactiveLogin() {
		request("/login", "10.0.0.9");
		logger.onInteractiveLogin(new InteractiveAuthenticationSuccessEvent(webUser("192.168.1.5"), getClass()));

		assertThat(capture.events()).singleElement().satisfies(e -> {
			assertThat(e.getLevel()).isEqualTo(Level.INFO);
			assertThat(e.getFormattedMessage())
					.startsWith("[auth.login] user=hong.gildong roles=[ROLE_ADMIN] channel=web ip=192.168.1.5");
		});
	}

	@Test
	@DisplayName("게스트 토큰 성공 — DEBUG [auth.authenticated] 채널 guest · 이름은 게스트 id · 토큰 값은 어디에도 없다")
	void guestAuthenticated_noSecret() {
		request("/api/pxe/v1/agent/checkin", "10.0.2.15");
		GuestAuthenticationToken auth = GuestAuthenticationToken.authenticated(
				new GuestPrincipal(
						java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"), java.util.UUID.randomUUID()));
		logger.onAuthenticated(new AuthenticationSuccessEvent(auth));

		assertThat(capture.events()).singleElement().satisfies(e -> {
			assertThat(e.getLevel()).isEqualTo(Level.DEBUG);
			assertThat(e.getFormattedMessage())
					.contains("[auth.authenticated] user=11111111-2222-3333-4444-555555555555")
					.contains("channel=guest ip=10.0.2.15")
					.doesNotContain(SECRET_TOKEN);
		});
	}

	@Test
	@DisplayName("실패 — WARN [auth.login.failed] 사유 코드는 이벤트 클래스 이름에서(BAD_CREDENTIALS · DISABLED · LOCKED) · 미인증 게스트 토큰의 값은 없다")
	void failures() {
		request("/api/pxe/v1/agent/checkin", "10.0.2.15");
		Authentication guest = GuestAuthenticationToken.unauthenticated(new GuestCredential.HeaderToken(SECRET_TOKEN));
		logger.onFailure(new AuthenticationFailureBadCredentialsEvent(guest, new BadCredentialsException("게스트 credential 이 유효하지 않습니다.")));

		request("/login", "192.168.1.5");
		Authentication web = UsernamePasswordAuthenticationToken.unauthenticated("hong.gildong", "wrong");
		logger.onFailure(new AuthenticationFailureDisabledEvent(web, new DisabledException("disabled")));
		logger.onFailure(new AuthenticationFailureLockedEvent(web, new LockedException("locked")));

		assertThat(capture.events()).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
		assertThat(capture.lines()).satisfiesExactly(
				l -> assertThat(l).startsWith("[auth.login.failed] user=guest reason=BAD_CREDENTIALS channel=guest ip=10.0.2.15")
						.doesNotContain(SECRET_TOKEN),
				l -> assertThat(l).startsWith("[auth.login.failed] user=hong.gildong reason=DISABLED channel=web ip=192.168.1.5")
						.doesNotContain("wrong"),
				l -> assertThat(l).startsWith("[auth.login.failed] user=hong.gildong reason=LOCKED channel=web"));
	}

	@Test
	@DisplayName("사유 코드 변환 — CamelCase 이벤트 이름이 UPPER_SNAKE 가 된다")
	void reasonCodes() {
		Authentication web = UsernamePasswordAuthenticationToken.unauthenticated("u", "p");
		assertThat(AuthenticationEventLogger.reasonOf(new org.springframework.security.authentication.event.AuthenticationFailureCredentialsExpiredEvent(
				web, new org.springframework.security.authentication.CredentialsExpiredException("x")))).isEqualTo("CREDENTIALS_EXPIRED");
		assertThat(AuthenticationEventLogger.reasonOf(new org.springframework.security.authentication.event.AuthenticationFailureProviderNotFoundEvent(
				web, new org.springframework.security.authentication.ProviderNotFoundException("x")))).isEqualTo("PROVIDER_NOT_FOUND");
	}

	@Test
	@DisplayName("로그아웃 — INFO [auth.logout]")
	void logout() {
		logger.onLogout(new LogoutSuccessEvent(webUser("192.168.1.5")));
		assertThat(capture.events()).singleElement().satisfies(e -> {
			assertThat(e.getLevel()).isEqualTo(Level.INFO);
			assertThat(e.getFormattedMessage()).isEqualTo("[auth.logout] user=hong.gildong ip=192.168.1.5");
		});
	}

	@Test
	@DisplayName("인가 거절 — 인증된 사용자는 WARN(경로 · 메서드) · 익명은 DEBUG(로그인 유도가 정상) · LAN 밖 익명은 WARN")
	void authorizationDenied() {
		MockHttpServletRequest forbidden = request("/system/asset", "192.168.1.5");
		forbidden.setMethod("POST");
		logger.onAuthorizationDenied(new AuthorizationDeniedEvent<>(() -> webUser("192.168.1.5"),
				forbidden, new AuthorizationDecision(false)));

		MockHttpServletRequest beforeLogin = request("/management/os", "192.168.1.6");
		Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		logger.onAuthorizationDenied(new AuthorizationDeniedEvent<>(() -> anonymous,
				beforeLogin, new AuthorizationDecision(false)));

		MockHttpServletRequest outsideLan = request("/api/pxe/v1/boot", "172.16.0.5");
		logger.onAuthorizationDenied(new AuthorizationDeniedEvent<>(() -> anonymous, outsideLan,
				new ProvisioningLanPolicy(java.util.List.of("10.0.2.0/24")).authorize(() -> anonymous, new RequestAuthorizationContext(outsideLan))));

		assertThat(capture.events()).satisfiesExactly(
				e -> {
					assertThat(e.getLevel()).isEqualTo(Level.WARN);
					assertThat(e.getFormattedMessage())
							.isEqualTo("[authz.denied] user=hong.gildong method=POST path=/system/asset channel=web ip=192.168.1.5 result=AuthorizationDecision");
				},
				e -> {
					assertThat(e.getLevel()).isEqualTo(Level.DEBUG);
					assertThat(e.getFormattedMessage()).startsWith("[authz.denied] user=anonymous method=GET path=/management/os");
				},
				e -> {
					assertThat(e.getLevel()).isEqualTo(Level.WARN);
					assertThat(e.getFormattedMessage())
							.isEqualTo("[authz.denied] user=anonymous method=GET path=/api/pxe/v1/boot channel=guest ip=172.16.0.5 result=Denied");
				});
	}

	@Test
	@DisplayName("계정 사건 — 가입은 mode · 역할 · 초기 비밀번호 여부, 비밀번호 변경은 강제/자발 · 비밀번호 자체는 이벤트에 없다")
	void accountEvents() {
		logger.onAccountEvent(AccountAuditEvent.created("probe1", "hong.gildong", "ADMIN_CREATES_USER", Set.of("ROLE_USER"), true));
		logger.onAccountEvent(AccountAuditEvent.passwordChanged("probe1", "probe1", true));

		assertThat(capture.lines()).containsExactly(
				"[auth.account.created] user=probe1 by=hong.gildong mode=ADMIN_CREATES_USER roles=[ROLE_USER] mustChangePassword=true",
				"[auth.password.changed] user=probe1 by=probe1 mode=FORCED");
	}
}
