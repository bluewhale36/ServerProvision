package com.example.serverprovision.execution.pxeinfra.validation;

import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R16 — 모드별 필수 집합과 형식 · 교차 검증이 필드 단위 위반으로 귀결하는지. PROXY 는 주소 배정 필드를 보지 않는다.
 */
class PxeNetworkConfigValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("AUTHORITATIVE 유효값 — 위반 0")
    void authoritativeValid() {
        assertThat(validator.validate(authoritative("10.0.2.100", "10.0.2.200", "10.0.2.1", "8.8.8.8", 600L))).isEmpty();
    }

    @Test
    @DisplayName("PROXY — 서브넷 · 부트 서버만 있으면 유효 · 주소 배정 칸에 쓰레기가 와도 위반 없음(서버가 버린다)")
    void proxyIgnoresAddressing() {
        PxeNetworkConfigRequest clean = new PxeNetworkConfigRequest(DhcpMode.PROXY, "10.1.1.0/24",
                "", "", "", "", "", "10.1.1.17", null, "");
        PxeNetworkConfigRequest junk = new PxeNetworkConfigRequest(DhcpMode.PROXY, "10.1.1.0/24",
                "not-an-ip", "fe80::1", "x", "y", "z", "10.1.1.17", -5L, "");
        assertThat(validator.validate(clean)).isEmpty();
        assertThat(validator.validate(junk)).isEmpty();
    }

    @Test
    @DisplayName("AUTHORITATIVE — 주소 배정 필드가 비면 필드마다 필수 위반")
    void authoritativeRequiresAddressing() {
        Set<ConstraintViolation<PxeNetworkConfigRequest>> violations = validator.validate(
                new PxeNetworkConfigRequest(DhcpMode.AUTHORITATIVE, "10.0.2.0/24", "", "", "", "", "", "10.0.2.2", null, ""));
        assertThat(paths(violations)).containsExactlyInAnyOrder("rangeStart", "rangeEnd", "routers", "primaryDns", "leaseSeconds");
    }

    @Test
    @DisplayName("AUTHORITATIVE — 범위 이탈은 rangeEnd 에, IPv6 은 그 필드에, 임대 0 은 leaseSeconds 에")
    void authoritativeFieldViolations() {
        assertThat(paths(validator.validate(authoritative("10.0.2.100", "10.0.3.5", "10.0.2.1", "8.8.8.8", 600L))))
                .containsExactly("rangeEnd");
        assertThat(paths(validator.validate(authoritative("fe80::1", "10.0.2.200", "10.0.2.1", "8.8.8.8", 600L))))
                .containsExactly("rangeStart");
        assertThat(paths(validator.validate(authoritative("10.0.2.100", "10.0.2.200", "10.0.2.1", "8.8.8.8", 0L))))
                .containsExactly("leaseSeconds");
    }

    @Test
    @DisplayName("두 모드 공통 — 모드 누락 · CIDR 형식 · 부트 서버 형식")
    void commonViolations() {
        assertThat(paths(validator.validate(new PxeNetworkConfigRequest(null, "10.1.1.0/24",
                "", "", "", "", "", "10.1.1.17", null, "")))).containsExactly("dhcpMode");
        assertThat(paths(validator.validate(new PxeNetworkConfigRequest(DhcpMode.PROXY, "10.1.1.5/24",
                "", "", "", "", "", "10.1.1.17", null, "")))).containsExactly("subnetCidr");   // host bit ≠ 0
        assertThat(paths(validator.validate(new PxeNetworkConfigRequest(DhcpMode.PROXY, "10.1.1.0/24",
                "", "", "", "", "", "nope", null, "")))).containsExactly("bootServerIp");
    }

    private static PxeNetworkConfigRequest authoritative(String start, String end, String routers, String dns, Long lease) {
        return new PxeNetworkConfigRequest(DhcpMode.AUTHORITATIVE, "10.0.2.0/24", start, end, routers, dns, "",
                "10.0.2.2", lease, "");
    }

    private static Set<String> paths(Set<ConstraintViolation<PxeNetworkConfigRequest>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
