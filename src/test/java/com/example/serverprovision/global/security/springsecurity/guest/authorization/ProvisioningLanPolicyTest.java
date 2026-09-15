package com.example.serverprovision.global.security.springsecurity.guest.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S19-2 D-3 — 출발지 대역 판정: CIDR 안/밖 · 다중 CIDR · 목록 비면 제한 없음 · IPv6 출발지 · 잘못된 CIDR 은 기동 실패. */
class ProvisioningLanPolicyTest {

	private static AuthorizationResult decide(ProvisioningLanPolicy policy, String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pxe/v1/boot");
		request.setRemoteAddr(remoteAddr);
		return policy.authorize(() -> null, new RequestAuthorizationContext(request));
	}

	@Test
	@DisplayName("단일 CIDR — 안은 허용 · 밖은 Denied(출발지 동반)")
	void singleCidr() {
		ProvisioningLanPolicy policy = new ProvisioningLanPolicy("192.168.1.0/24");
		assertThat(decide(policy, "192.168.1.150").isGranted()).isTrue();
		AuthorizationResult denied = decide(policy, "192.168.2.7");
		assertThat(denied.isGranted()).isFalse();
		assertThat(denied).isInstanceOf(ProvisioningLanPolicy.Denied.class);
		assertThat(((ProvisioningLanPolicy.Denied) denied).remoteAddress()).isEqualTo("192.168.2.7");
	}

	@Test
	@DisplayName("다중 CIDR(쉼표 · 공백 허용) — 하나라도 맞으면 허용 · IPv6 출발지는 IPv4 CIDR 에 맞지 않는다")
	void multipleCidrs() {
		ProvisioningLanPolicy policy = new ProvisioningLanPolicy(" 127.0.0.1/32, 10.0.2.0/24 ,::1/128");
		assertThat(decide(policy, "127.0.0.1").isGranted()).isTrue();
		assertThat(decide(policy, "10.0.2.15").isGranted()).isTrue();
		assertThat(decide(policy, "0:0:0:0:0:0:0:1").isGranted()).isTrue();
		assertThat(decide(policy, "10.0.3.1").isGranted()).isFalse();
		assertThat(policy.unrestricted()).isFalse();
	}

	@Test
	@DisplayName("목록 비면 제한 없음 — 어떤 출발지도 허용(기동 WARN 은 로그)")
	void empty_unrestricted() {
		for (String cidrs : new String[]{"", " ", null}) {
			ProvisioningLanPolicy policy = new ProvisioningLanPolicy(cidrs);
			assertThat(policy.unrestricted()).isTrue();
			assertThat(decide(policy, "203.0.113.9").isGranted()).isTrue();
		}
		assertThat(new ProvisioningLanPolicy(List.of()).unrestricted()).isTrue();
	}

	@Test
	@DisplayName("잘못된 CIDR — 기동 실패(조용히 무제한이 되지 않는다)")
	void malformed_failsFast() {
		assertThatThrownBy(() -> new ProvisioningLanPolicy("192.168.1/24x"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("pxe.guest.allowed-cidrs");
	}
}
