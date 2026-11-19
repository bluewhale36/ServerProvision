package com.example.serverprovision.global.security.springsecurity.authorization;

import org.springframework.security.authorization.AuthorizationDecision;

/**
 * 사람용 사유를 실은 거절 결정(HF29). 거절을 내린 정책이 사유의 주인이다 — 경로 → 문장 표를 핸들러에 두면 보호 경로가 늘 때마다
 * 표가 자란다. {@link ReasonedAccessDeniedHandler} 가 {@code AuthorizationDeniedException} 에서 이 결정을 꺼내 화면 · JSON 에 싣는다
 * (게스트 체인 {@code ProvisioningLanPolicy.Denied} 와 같은 모양).
 */
public class ReasonedDenial extends AuthorizationDecision {

	private final String reason;

	public ReasonedDenial(String reason) {
		super(false);
		this.reason = reason;
	}

	public String reason() {
		return reason;
	}
}
