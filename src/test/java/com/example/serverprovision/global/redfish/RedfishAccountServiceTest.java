package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * E1.6 CP4 — 계정 비밀번호 교체의 실측 계약(E0-4-1): 컬렉션에서 UserName 매칭으로 계정을 찾고
 * (id 하드코딩 불가 — admin 이 두 번째 계정), fresh ETag 를 If-Match 로 되돌려 PATCH 한다.
 */
class RedfishAccountServiceTest {

    private static final String BASE = "https://10.0.0.9";
    private static final BmcCredentials FACTORY = new BmcCredentials("admin", "SERIAL123", "공장 기본(보드 시리얼)");

    private MockRestServiceServer server;
    private RedfishAccountService accountService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        accountService = new RedfishAccountService(new RedfishClient(builder.build(), new ObjectMapper(), 443));
    }

    private void expectCollection() {
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"Members":[{"@odata.id":"/redfish/v1/AccountService/Accounts/1"},
                                    {"@odata.id":"/redfish/v1/AccountService/Accounts/2"}]}
                        """, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("changePassword — UserName 매칭으로 admin(두 번째 계정)을 찾아 fresh ETag 로 If-Match PATCH")
    void changePassword_matchesByUserName_patchesWithFreshEtag() {
        expectCollection();
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts/1"))
                .andRespond(withSuccess("{\"UserName\":\"anonymous\"}", MediaType.APPLICATION_JSON));
        HttpHeaders withEtag = new HttpHeaders();
        withEtag.setETag("W/\"1000\"");
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts/2"))
                .andRespond(withSuccess("{\"UserName\":\"admin\"}", MediaType.APPLICATION_JSON).headers(withEtag));
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts/2"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "W/\"1000\""))
                .andExpect(content().json("{\"Password\":\"new-standard\"}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        accountService.changePassword("10.0.0.9", FACTORY, "admin", "new-standard");
        server.verify();
    }

    @Test
    @DisplayName("changePassword — 대상 계정이 없으면 NOT_FOUND 로 던진다")
    void changePassword_userMissing_throwsNotFound() {
        expectCollection();
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts/1"))
                .andRespond(withSuccess("{\"UserName\":\"anonymous\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts/2"))
                .andRespond(withSuccess("{\"UserName\":\"operator\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> accountService.changePassword("10.0.0.9", FACTORY, "admin", "x"))
                .isInstanceOf(RedfishRequestException.class)
                .extracting(e -> ((RedfishRequestException) e).getError())
                .isEqualTo(RedfishError.NOT_FOUND);
    }

    @Test
    @DisplayName("accounts — 401 은 AUTH_FAILED 로 분류돼 전파된다(사다리의 자격 탐침 재료)")
    void accounts_unauthorized_classified() {
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> accountService.accounts("10.0.0.9", FACTORY))
                .isInstanceOf(RedfishRequestException.class)
                .extracting(e -> ((RedfishRequestException) e).getError())
                .isEqualTo(RedfishError.AUTH_FAILED);
    }

    @Test
    @DisplayName("changePassword — 낡은 ETag 의 412 는 PRECONDITION_FAILED 로 분류된다(동시 사다리 경합)")
    void changePassword_staleEtag_classified() {
        expectCollection();
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts/1"))
                .andRespond(withSuccess("{\"UserName\":\"admin\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/redfish/v1/AccountService/Accounts/1"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        assertThatThrownBy(() -> accountService.changePassword("10.0.0.9", FACTORY, "admin", "x"))
                .isInstanceOf(RedfishRequestException.class)
                .extracting(e -> ((RedfishRequestException) e).getError())
                .isEqualTo(RedfishError.PRECONDITION_FAILED);
    }
}
