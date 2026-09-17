package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures;
import com.example.serverprovision.execution.pxeinfra.apply.ApplyOutcome;
import com.example.serverprovision.execution.pxeinfra.apply.DhcpConfigApplyService;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigMapper;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R16 — PXE 네트워크 구성 폼의 HTTP 계층 검증(테스트 규율 4범주). 두 모드의 성공 302 PRG, 400(모드별 필수 · 형식 · 교차 위반
 * + REJECTED dnsmasq --test 거절), 500(ROLLED_BACK · RESTORE_FAILED · 게이트 실행불능)을 다룬다. 컨트롤러가 {@code applyAndRecord}
 * 귀결을 {@code throwIfNotApplied} 로 승격하는 실경로로 각각 트리거해 {@code handleDomain} advice 매핑을 실제로 통과한다.
 */
@WebMvcTest(controllers = PxeNetworkConfigController.class)
class PxeNetworkConfigControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PxeNetworkConfigService configService;
    @MockitoBean
    DhcpConfigApplyService applyService;
    @MockitoBean
    PxeNetworkConfigMapper mapper;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    /** 자체 DHCP 모드의 유효 폼 파라미터 기본값. 개별 테스트가 문제 필드만 교체한다. */
    private static Map<String, String> authoritativeParams() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("dhcpMode", "AUTHORITATIVE");
        p.put("subnetCidr", "10.0.2.0/24");
        p.put("rangeStart", "10.0.2.100");
        p.put("rangeEnd", "10.0.2.200");
        p.put("routers", "10.0.2.1");
        p.put("primaryDns", "8.8.8.8");
        p.put("secondaryDns", "");
        p.put("bootServerIp", "10.0.2.2");
        p.put("leaseSeconds", "600");
        p.put("domainName", "");
        return p;
    }

    /** proxyDHCP 모드 — 서브넷 · 부트 서버만(disabled 그룹은 제출되지 않는다). */
    private static Map<String, String> proxyParams() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("dhcpMode", "PROXY");
        p.put("subnetCidr", "10.1.1.0/24");
        p.put("bootServerIp", "10.1.1.17");
        return p;
    }

    private static MockHttpServletRequestBuilder form(Map<String, String> base, String... overrides) {
        Map<String, String> p = new LinkedHashMap<>(base);
        for (int i = 0; i < overrides.length; i += 2) {
            p.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder request = post("/system/pxe-infra/network");
        p.forEach(request::param);
        return request;
    }

    private void givenApplyOutcome(ApplyOutcome outcome) {
        // mapper 는 mock 이라 desired 가 null 로 넘어올 수 있어 any()(null 허용)로 매칭한다.
        given(applyService.applyAndRecord(any(), any())).willReturn(outcome);
    }

    // ── 폼 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET — 저장 전 폼 200 · 모드 라디오 둘 · dnsmasq 문구")
    void form_unsaved() throws Exception {
        mvc.perform(get("/system/pxe-infra/network"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PXE 네트워크 구성 (dnsmasq)")))
                .andExpect(content().string(containsString("value=\"AUTHORITATIVE\"")))
                .andExpect(content().string(containsString("value=\"PROXY\"")))
                .andExpect(content().string(containsString("data-mode-group=\"AUTHORITATIVE\"")));
    }

    @Test
    @DisplayName("GET — 저장된 proxyDHCP 구성이면 모드 배지와 서브넷 · 부트 서버 프리필")
    void form_savedProxy() throws Exception {
        given(configService.load()).willReturn(Optional.of(PxeNetworkConfigFixtures.proxy()));

        mvc.perform(get("/system/pxe-infra/network"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("proxyDHCP")))
                .andExpect(content().string(containsString("10.1.1.0/24")))
                .andExpect(content().string(containsString("10.1.1.17")));
    }

    // ── 성공 2xx(PRG) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("성공 — 자체 DHCP 검증 통과 + APPLIED → 302 PRG")
    void submit_authoritative_applied_redirects() throws Exception {
        givenApplyOutcome(ApplyOutcome.applied(5L));

        mvc.perform(form(authoritativeParams()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/pxe-infra/network"));
    }

    @Test
    @DisplayName("성공 — proxyDHCP 는 서브넷 · 부트 서버만으로 통과 → 302 PRG")
    void submit_proxy_applied_redirects() throws Exception {
        givenApplyOutcome(ApplyOutcome.applied(null));

        mvc.perform(form(proxyParams()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/pxe-infra/network"));
    }

    @Test
    @DisplayName("성공 — proxyDHCP 직접 POST 에 주소 배정 쓰레기 값이 와도 무시하고 302(서버가 버린다)")
    void submit_proxy_ignoresAddressingJunk() throws Exception {
        givenApplyOutcome(ApplyOutcome.applied(null));

        mvc.perform(form(proxyParams(), "rangeStart", "not-an-ip", "rangeEnd", "fe80::1", "leaseSeconds", "-1"))
                .andExpect(status().is3xxRedirection());
    }

    // ── 400 필드 검증 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("400 — 모드 누락")
    void submit_missingMode_badRequest() throws Exception {
        Map<String, String> p = authoritativeParams();
        p.remove("dhcpMode");
        mvc.perform(form(p)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — 잘못된 CIDR(두 모드 공통)")
    void submit_invalidCidr_badRequest() throws Exception {
        mvc.perform(form(proxyParams(), "subnetCidr", "not-a-cidr")).andExpect(status().isBadRequest());
        mvc.perform(form(authoritativeParams(), "subnetCidr", "10.0.2.5/24")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — 자체 DHCP 에서 범위 누락 · IPv6 · 서브넷 이탈 · 임대 0")
    void submit_authoritative_fieldViolations() throws Exception {
        mvc.perform(form(authoritativeParams(), "rangeStart", "")).andExpect(status().isBadRequest());
        mvc.perform(form(authoritativeParams(), "rangeStart", "fe80::1")).andExpect(status().isBadRequest());
        mvc.perform(form(authoritativeParams(), "rangeEnd", "10.0.3.50")).andExpect(status().isBadRequest());
        mvc.perform(form(authoritativeParams(), "leaseSeconds", "0")).andExpect(status().isBadRequest());
    }

    // ── 400 REJECTED(dnsmasq --test 거절) ─────────────────────────────────────

    @Test
    @DisplayName("400 — REJECTED(dnsmasq --test 문법 거절) → DhcpConfigInvalidException")
    void submit_rejected_badRequest() throws Exception {
        givenApplyOutcome(ApplyOutcome.rejected("dnsmasq: bad option at line 3"));

        mvc.perform(form(authoritativeParams()))
                .andExpect(status().isBadRequest());
    }

    // ── 500 서버/인프라 실패 ──────────────────────────────────────────────────

    @Test
    @DisplayName("500 — ROLLED_BACK(재기동 실패 · 복원) → DhcpServiceControlFailedException")
    void submit_rolledBack_serverError() throws Exception {
        givenApplyOutcome(ApplyOutcome.rolledBack("재기동 실패, 이전 구성으로 복원"));

        mvc.perform(form(proxyParams()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("500 — 게이트 실행불능(ROLLED_BACK 재사용) → DhcpServiceControlFailedException")
    void submit_gateUnexecutable_serverError() throws Exception {
        givenApplyOutcome(ApplyOutcome.rolledBack("dnsmasq 문법 검사(dnsmasq --test)를 실행할 수 없습니다 : NOT_FOUND"));

        mvc.perform(form(authoritativeParams()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("500 — RESTORE_FAILED(복원까지 실패) → DhcpConfigRestoreFailedException")
    void submit_restoreFailed_serverError() throws Exception {
        givenApplyOutcome(ApplyOutcome.restoreFailed("수동 복구 필요 — systemctl restart dnsmasq"));

        mvc.perform(form(authoritativeParams()))
                .andExpect(status().isInternalServerError());
    }
}
