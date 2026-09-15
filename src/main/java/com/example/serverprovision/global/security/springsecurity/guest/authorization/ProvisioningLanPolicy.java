package com.example.serverprovision.global.security.springsecurity.guest.authorization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * 프로비저닝 LAN 출발지 제한(S19-2 D-3) — 게스트 채널 전체에 걸린다. {@code pxe.guest.allowed-cidrs} 의 CIDR 하나에라도
 * 맞으면 허용, 비어 있으면 제한 없음(샌드박스 · 랩 편의 · 기동 WARN). nginx 뒤에서는 {@code server.tomcat.remoteip.*} 가
 * {@code X-Forwarded-For} 를 출발지로 되돌려 놓는다(같은 호스트 프록시만 신뢰).
 * <p>거절은 {@link Denied} 로 돌려 AccessDeniedHandler가 권한 부족(403 GUEST_FORBIDDEN)과 구분해 응답한다.</p>
 */
@Slf4j
@Component
public class ProvisioningLanPolicy implements AuthorizationManager<RequestAuthorizationContext> {

	private final List<IpAddressMatcher> allowed;

	@Autowired   // 생성자가 둘이라 빈 생성자를 명시한다 — 목록 생성자는 테스트 · 조립용
	public ProvisioningLanPolicy(@Value("${pxe.guest.allowed-cidrs:}") String cidrs) {
		this(cidrs == null || cidrs.isBlank() ? List.of()
				: Arrays.stream(cidrs.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
	}

	public ProvisioningLanPolicy(List<String> cidrs) {
		try {
			this.allowed = cidrs.stream().map(IpAddressMatcher::new).toList();
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("pxe.guest.allowed-cidrs 에 올바르지 않은 CIDR 이 있다 (PXE_GUEST_ALLOWED_CIDRS) : " + cidrs, ex);
		}
		if (allowed.isEmpty()) {
			log.warn("[security] pxe.guest.allowed-cidrs 미설정 — 게스트 채널의 출발지 제한이 없다. 배포에서는 PXE_GUEST_ALLOWED_CIDRS 를 설정한다.");
		} else {
			log.info("[security] 게스트 채널 출발지 제한 : {}", cidrs);
		}
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
		if (allowed.isEmpty()) {
			return new AuthorizationDecision(true);
		}
		String remote = context.getRequest().getRemoteAddr();
		if (remote != null && allowed.stream().anyMatch(m -> m.matches(remote))) {
			return new AuthorizationDecision(true);
		}
		return new Denied(remote);
	}

	public boolean unrestricted() {
		return allowed.isEmpty();
	}

	/** 출발지가 프로비저닝 LAN 밖 — AccessDeniedHandler가 이 타입으로 403 코드를 고른다. */
	public static final class Denied extends AuthorizationDecision {

		private final String remoteAddress;

		Denied(String remoteAddress) {
			super(false);
			this.remoteAddress = remoteAddress;
		}

		public String remoteAddress() {
			return remoteAddress;
		}
	}
}
