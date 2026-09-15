package com.example.serverprovision.global.security.springsecurity.config;

import com.example.serverprovision.execution.config.PxeBootProperties;
import com.example.serverprovision.global.security.springsecurity.domain.MachineAuthority;
import com.example.serverprovision.global.security.springsecurity.guest.authentication.GuestCredentialConverter;
import com.example.serverprovision.global.security.springsecurity.guest.authentication.GuestPrincipalResolver;
import com.example.serverprovision.global.security.springsecurity.guest.authentication.GuestTokenAuthenticationProvider;
import com.example.serverprovision.global.security.springsecurity.guest.authentication.PxeBootAuthenticationProvider;
import com.example.serverprovision.global.security.springsecurity.guest.authorization.ProvisioningLanPolicy;
import com.example.serverprovision.global.security.springsecurity.guest.web.GuestAccessDeniedHandler;
import com.example.serverprovision.global.security.springsecurity.guest.web.GuestAuthenticationEntryPoint;
import com.example.serverprovision.global.security.springsecurity.guest.web.PxeBootEntryPoint;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * 게스트 채널 전용 필터 체인(S19-1 D-1 · S19-2) — {@code /api/pxe/v1/**} 는 세션 · CSRF · 로그인 리다이렉트가 없는 기계 채널이다.
 * <ul>
 *   <li>게스트 토큰(헤더 · 경로 서빙 토큰)은 {@link AuthenticationFilter} 가 세운다 → {@code ROLE_GUEST}.</li>
 *   <li>첫 접촉({@code /boot} · {@code /assets/**})은 PXE 부팅 계정의 Basic credential 을 {@code httpBasic} 이 세운다 → {@code ROLE_PXE_BOOT}(S19-2 D-2).
 *       두 필터가 같은 {@link ProviderManager} 를 쓴다.</li>
 *   <li>인가는 프로비저닝 LAN ∧ 역할(S19-2 D-3) — 대역 밖은 403 {@code PXE_LAN_FORBIDDEN}.</li>
 *   <li>거절 응답은 경로별로 갈린다(D-5): 첫 접촉은 401 text + {@code WWW-Authenticate}, 나머지는 401 · 403 JSON.</li>
 * </ul>
 * 웹 화면 체인({@link SecurityConfig} · {@code @Order(2)})은 이 매처에 걸리지 않는 나머지를 받는다.
 */
@Configuration
@RequiredArgsConstructor
public class GuestSecurityConfig {

	private static final String BOOT_PATH = "/api/pxe/v1/boot";
	private static final String ASSETS_PATTERN = "/api/pxe/v1/assets/**";
	/**
	 * 진단 리눅스의 firstboot 가 커널 인자 provision_base(credential 없음)로 받는 에이전트 본체(S19-2 CP5 F-1). 이미지를 바꾸지 않고
	 * 진단이 서게 하려면 이 한 파일만 credential 없이 연다 — 저장소의 공개 스크립트라 secret 이 없고, 토큰 없이는 아무것도 못 한다. 대역 제한은 그대로.
	 * firstboot 가 provision_token 을 X-Guest-Token 으로 보내도록 이미지를 고치면 이 예외를 닫는다(적립).
	 */
	private static final String AGENT_SCRIPT_PATH = "/api/pxe/v1/assets/agent.sh";

	private final GuestPrincipalResolver guestPrincipalResolver;
	private final PxeBootProperties pxeBootProperties;
	private final ProvisioningLanPolicy provisioningLanPolicy;
	private final GuestAuthenticationEntryPoint guestAuthenticationEntryPoint;
	private final PxeBootEntryPoint pxeBootEntryPoint;
	private final GuestAccessDeniedHandler guestAccessDeniedHandler;

	@Bean
	@Order(1)
	public SecurityFilterChain guestSecurityFilterChain(HttpSecurity http) throws Exception {
		// 해당 Provider 는 bean 으로 두지 않는다 — AuthenticationProvider bean 은 전역 AuthenticationManager 를 독점해 웹 formLogin 을 끊는다(S19-1 CP5 F-1).
		ProviderManager guestManager = new ProviderManager(
				new GuestTokenAuthenticationProvider(guestPrincipalResolver),
				new PxeBootAuthenticationProvider(pxeBootProperties)
		);
		AuthenticationFilter guestFilter = new AuthenticationFilter(guestManager, new GuestCredentialConverter());
		guestFilter.setSuccessHandler(
				(request, response, authentication) -> { }
		); // 확립한 Principal 로 Chain 을 지속한다.
		guestFilter.setFailureHandler(guestAuthenticationEntryPoint); // permitAll 경로라도 틀린 토큰은 401
		guestFilter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());

		http
				.securityMatcher("/api/pxe/v1/**")

				.csrf(AbstractHttpConfigurer::disable)

				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))

				.authenticationManager(guestManager)

				// 최초 접촉의 Basic Auth — 틀린 secret 은 httpBasic 의 entry point(401 text + WWW-Authenticate)으로 끝난다
				.httpBasic(basic -> basic.authenticationEntryPoint(pxeBootEntryPoint))

				.addFilterBefore(guestFilter, AuthorizationFilter.class)

				.authorizeHttpRequests(req -> req
						.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
						.requestMatchers(AGENT_SCRIPT_PATH).access(lanOnly())
						.requestMatchers(BOOT_PATH, ASSETS_PATTERN).access(lanAnd(MachineAuthority.PXE_BOOT))
						.anyRequest().access(lanAnd(MachineAuthority.GUEST))
				)

				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(entryPointByPath())
						.accessDeniedHandler(guestAccessDeniedHandler)
				);

		return http.build();
	}

	/** 프로비저닝 LAN ∧ 역할 — 대역 판정이 앞이라 대역 밖은 credential 과 무관하게 LAN 거절로 끝난다. */
	private AuthorizationManager<RequestAuthorizationContext> lanAnd(MachineAuthority authority) {
		return AuthorizationManagers.allOf(provisioningLanPolicy, AuthorityAuthorizationManager.hasAuthority(authority.getAuthority()));
	}

	/** 프로비저닝 LAN 만 — credential 은 묻지 않는다(agent.sh). permitAll 로 두면 대역 제한까지 풀리므로 정책을 직접 건다. */
	private AuthorizationManager<RequestAuthorizationContext> lanOnly() {
		return provisioningLanPolicy;
	}

	/** credential 없음의 entry point — 최초 접촉은 Basic 을 요구하는 text, 나머지는 게스트 토큰을 요구하는 JSON. */
	private AuthenticationEntryPoint entryPointByPath() {
		PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
		return DelegatingAuthenticationEntryPoint.builder()
				.addEntryPointFor(pxeBootEntryPoint, new OrRequestMatcher(path.matcher(BOOT_PATH), path.matcher(ASSETS_PATTERN)))
				.defaultEntryPoint(guestAuthenticationEntryPoint)
				.build();
	}
}
