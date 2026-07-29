package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.apply.ApplyOutcome;
import com.example.serverprovision.execution.pxeinfra.apply.DhcpdConfigApplyService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-I-3-c — PXE 네트워크 구성 폼의 HTTP 계층 검증(테스트 규율 4범주). 성공 302 PRG, 400(필드 형식·교차 검증
 * 위반 + REJECTED dhcpd -t 거절), 500(ROLLED_BACK·RESTORE_FAILED·게이트 실행불능)을 다룬다. 신규 예외 3종은
 * 컨트롤러가 {@code applyAndRecord} 귀결을 {@code throwIfNotApplied} 로 승격하는 실경로로 각각 트리거되어
 * {@code handleDomain} advice 매핑을 실제로 통과한다(mocking 은 Service 단까지).
 */
@WebMvcTest(controllers = PxeNetworkConfigController.class)
class PxeNetworkConfigControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PxeNetworkConfigService configService;
    @MockitoBean
    DhcpdConfigApplyService applyService;
    @MockitoBean
    PxeNetworkConfigMapper mapper;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    /** 검증을 통과하는 유효 폼 파라미터의 기본값. 개별 테스트가 문제 필드만 교체한다(중복 파라미터 회피). */
    private static Map<String, String> validParams() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("subnetCidr", "10.0.2.0/24");
        p.put("rangeStart", "10.0.2.100");
        p.put("rangeEnd", "10.0.2.200");
        p.put("routers", "10.0.2.1");
        p.put("primaryDns", "8.8.8.8");
        p.put("secondaryDns", "");
        p.put("bootServerIp", "10.0.2.2");
        p.put("defaultLeaseSeconds", "600");
        p.put("maxLeaseSeconds", "7200");
        p.put("domainName", "");
        return p;
    }

    /** 기본값에 overrides(키,값 쌍)를 덮어쓴 뒤 단일값 폼 POST 요청을 만든다. */
    private static MockHttpServletRequestBuilder form(String... overrides) {
        Map<String, String> p = validParams();
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

    // ── 성공 2xx(PRG) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("성공 — 검증 통과 + APPLIED → 302 PRG")
    void submit_applied_redirects() throws Exception {
        givenApplyOutcome(ApplyOutcome.applied(5L));

        mvc.perform(form())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/pxe-infra/network"));
    }

    // ── 400 필드 검증 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("400 — 잘못된 CIDR")
    void submit_invalidCidr_badRequest() throws Exception {
        mvc.perform(form("subnetCidr", "not-a-cidr"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — IPv6 주소(IpAddressVO 거절)")
    void submit_ipv6_badRequest() throws Exception {
        mvc.perform(form("rangeStart", "fe80::1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — 리스 범위가 서브넷 경계를 벗어남")
    void submit_rangeOutsideSubnet_badRequest() throws Exception {
        mvc.perform(form("rangeEnd", "10.0.3.50"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 — max-lease < default-lease")
    void submit_maxLessThanDefault_badRequest() throws Exception {
        mvc.perform(form("defaultLeaseSeconds", "7200", "maxLeaseSeconds", "600"))
                .andExpect(status().isBadRequest());
    }

    // ── 400 REJECTED(dhcpd -t 거절) ──────────────────────────────────────────

    @Test
    @DisplayName("400 — REJECTED(dhcpd -t 문법 거절) → DhcpConfigInvalidException")
    void submit_rejected_badRequest() throws Exception {
        givenApplyOutcome(ApplyOutcome.rejected("dhcpd.conf line 3: syntax error"));

        mvc.perform(form())
                .andExpect(status().isBadRequest());
    }

    // ── 500 서버/인프라 실패 ──────────────────────────────────────────────────

    @Test
    @DisplayName("500 — ROLLED_BACK(재기동 실패·복원) → DhcpServiceControlFailedException")
    void submit_rolledBack_serverError() throws Exception {
        givenApplyOutcome(ApplyOutcome.rolledBack("재기동 실패, 이전 구성으로 복원"));

        mvc.perform(form())
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("500 — 게이트 실행불능(ROLLED_BACK 재사용) → DhcpServiceControlFailedException")
    void submit_gateUnexecutable_serverError() throws Exception {
        givenApplyOutcome(ApplyOutcome.rolledBack("dhcpd 문법 검사(dhcpd -t)를 실행할 수 없습니다 : NOT_FOUND"));

        mvc.perform(form())
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("500 — RESTORE_FAILED(복원까지 실패) → DhcpConfigRestoreFailedException")
    void submit_restoreFailed_serverError() throws Exception {
        givenApplyOutcome(ApplyOutcome.restoreFailed("수동 복구 필요 — systemctl restart dhcpd"));

        mvc.perform(form())
                .andExpect(status().isInternalServerError());
    }
}
