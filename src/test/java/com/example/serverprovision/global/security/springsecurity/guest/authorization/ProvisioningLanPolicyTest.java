package com.example.serverprovision.global.security.springsecurity.guest.authorization;

import com.example.serverprovision.global.systemsetting.event.ProvisioningAllowedRangeChangedEvent;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** S19-2 D-3 · HF35 — 출발지 대역 판정: 허용 대역 안/밖 · 루프백 · 대역 없으면 제한 없음 · 지연 적재 · 저장 이벤트 뒤 새 대역. */
class ProvisioningLanPolicyTest {

	private static AuthorizationResult decide(ProvisioningLanPolicy policy, String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pxe/v1/boot");
		request.setRemoteAddr(remoteAddr);
		return policy.authorize(() -> null, new RequestAuthorizationContext(request));
	}

	private static ProvisioningLanPolicy policyFor(String cidr) {
		return new ProvisioningLanPolicy(() -> Optional.of(ProvisioningAllowedCidr.parse(cidr)));
	}

	@Test
	@DisplayName("허용 대역 안은 허용 · 밖은 Denied(출발지 동반)")
	void insideAndOutside() {
		ProvisioningLanPolicy policy = policyFor("192.168.1.0/24");
		assertThat(decide(policy, "192.168.1.150").isGranted()).isTrue();
		AuthorizationResult denied = decide(policy, "192.168.2.7");
		assertThat(denied.isGranted()).isFalse();
		assertThat(denied).isInstanceOf(ProvisioningLanPolicy.Denied.class);
		assertThat(((ProvisioningLanPolicy.Denied) denied).remoteAddress()).isEqualTo("192.168.2.7");
		assertThat(policy.unrestricted()).isFalse();
	}

	@Test
	@DisplayName("루프백(IPv4 · IPv6)은 대역과 무관하게 허용 · 다른 IPv6 출발지는 IPv4 대역에 맞지 않는다")
	void loopbackAlwaysAllowed() {
		ProvisioningLanPolicy policy = policyFor("10.0.2.0/24");
		assertThat(decide(policy, "127.0.0.1").isGranted()).isTrue();
		assertThat(decide(policy, "0:0:0:0:0:0:0:1").isGranted()).isTrue();
		assertThat(decide(policy, "10.0.2.15").isGranted()).isTrue();
		assertThat(decide(policy, "fe80::1").isGranted()).isFalse();
	}

	@Test
	@DisplayName("허용 대역이 없으면 제한 없음 — 어떤 출발지도 허용")
	void unset_unrestricted() {
		ProvisioningLanPolicy policy = new ProvisioningLanPolicy(Optional::empty);
		assertThat(policy.unrestricted()).isTrue();
		assertThat(decide(policy, "203.0.113.9").isGranted()).isTrue();
	}

	@Test
	@DisplayName("대역은 첫 판정 때 한 번만 읽는다 — 생성 때 읽지 않고 요청마다 읽지도 않는다")
	void loadsLazilyOnce() {
		AtomicInteger reads = new AtomicInteger();
		ProvisioningLanPolicy policy = new ProvisioningLanPolicy(() -> {
			reads.incrementAndGet();
			return Optional.of(ProvisioningAllowedCidr.parse("10.1.1.0/24"));
		});
		assertThat(reads).hasValue(0);
		decide(policy, "10.1.1.5");
		decide(policy, "10.1.1.6");
		assertThat(reads).hasValue(1);
	}

	@Test
	@DisplayName("허용 대역 저장 이벤트 뒤에는 새 대역으로 판정한다(재기동 불필요)")
	void rangeChangedEventSwapsRange() {
		ProvisioningLanPolicy policy = policyFor("10.1.1.0/24");
		assertThat(decide(policy, "10.1.2.9").isGranted()).isFalse();

		policy.onRangeChanged(new ProvisioningAllowedRangeChangedEvent(ProvisioningAllowedCidr.parse("10.1.2.0/24")));

		assertThat(decide(policy, "10.1.2.9").isGranted()).isTrue();
		assertThat(decide(policy, "10.1.1.5").isGranted()).isFalse();
	}

	@Test
	@DisplayName("대역이 없던 정책도 저장 이벤트 뒤에는 제한을 건다")
	void unsetThenSaved() {
		ProvisioningLanPolicy policy = new ProvisioningLanPolicy(Optional::empty);
		assertThat(decide(policy, "203.0.113.9").isGranted()).isTrue();

		policy.onRangeChanged(new ProvisioningAllowedRangeChangedEvent(ProvisioningAllowedCidr.parse("10.1.1.0/24")));

		assertThat(policy.unrestricted()).isFalse();
		assertThat(decide(policy, "203.0.113.9").isGranted()).isFalse();
	}
}
