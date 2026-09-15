package com.example.serverprovision.global.security.springsecurity.config;

import com.example.serverprovision.global.security.springsecurity.authorization.MustChangePasswordSuccessHandler;
import com.example.serverprovision.global.security.springsecurity.authorization.SignupAccessPolicy;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

	private final SignupAccessPolicy signupAccessPolicy;
	private final MustChangePasswordSuccessHandler successHandler;

	@Bean
	@Order(2)   // S19-1 — 게스트 채널 체인(@Order(1)) 뒤의 웹 화면 체인
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(Customizer.withDefaults())

				.authorizeHttpRequests( req -> req
						// S18 D-8 — 비인증 화면(로그인 · 최초 관리자 등록)이 디자인 시스템 자산을 받아야 한다. 페이지 스크립트 디렉토리
						// (/management/** 등)는 컨트롤러 경로와 겹치므로 열지 않는다 — 인증 뒤에만 쓰인다.
						.requestMatchers("/global/**", "/css/**", "/fonts/**", "/vendor/**", "/favicon.ico").permitAll()
						// ERROR: 비인증 404 · 500 이 로그인으로 튀지 않게. ASYNC: SSE 처럼 첫 요청에서 이미 인가된 스트림의 비동기 디스패치가
						// 로그아웃 뒤 다시 인가에 걸려 "response already committed" ERROR 를 남기지 않게(CP5 F-4) — ASYNC 는 원 요청이 통과한 뒤에만 온다.
						.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
						.requestMatchers("/resources/**").permitAll()
						// /api/pxe/v1/** 는 S19-1 부터 GuestSecurityConfig(@Order(1)) 의 게스트 체인이 받는다 — 이 체인에는 오지 않는다.
						.requestMatchers("/login").permitAll()
						.requestMatchers("/signup").access(signupAccessPolicy)   // S18 D-12 — 관리자 부재면 누구나 · 있으면 ROLE_ADMIN
						.anyRequest().authenticated()
				)

				// S18 CP5 C3 — 세션이 끊긴 뒤의 XHR 폼 제출(form-submit.js · X-Requested-With)은 httpBasic 의 401 이 아니라 로그인 화면으로
				// 보낸다. 302 는 XhrRedirectFilter 가 200 + X-Redirect-Location 으로 바꾸고 form-submit.js 가 브라우저 이동으로 받는다(HF9 경로).
				// httpBasic 의 entry point 은 X-Requested-With 요청도 자기 것으로 잡으므로 이 매핑을 먼저 둔다.
				.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
						new LoginUrlAuthenticationEntryPoint("/login"),
						new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest")))

				.formLogin( form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.successHandler(successHandler)   // S18 D-14 — 초기 비밀번호 계정은 변경 화면으로
						.failureUrl("/login?error=true")
						.usernameParameter("username")
						.passwordParameter("password")
				)

				.logout( logout -> logout
						.logoutUrl("/logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID")
				);

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}
}
