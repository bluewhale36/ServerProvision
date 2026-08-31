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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * E1.5 CP4 — 저수준 HTTP 계층을 {@link MockRestServiceServer} 로 실행한다: Basic 헤더 · Content-Type ·
 * Task 경로 판독(Location / 본문 @odata.id) · 오류 분류(401 · 412 · IO). TLS 완화는 연결 계층이라 여기선 못 다루고
 * CP5 하네스(자가서명 mock-redfish)가 확인한다.
 */
class RedfishClientTest {

    private static final BmcCredentials CREDS = new BmcCredentials("admin", "pw", "표준 계정");

    private MockRestServiceServer server;
    private RedfishClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RedfishClient(builder.build(), new ObjectMapper(), 443);
    }

    @Test
    @DisplayName("GET — Basic 헤더가 붙고 JSON 이 파싱된다")
    void getJson() {
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Basic ")))
                .andRespond(withSuccess("{\"PowerState\":\"On\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getJson("10.0.0.9", CREDS, "/redfish/v1/Systems/Self").path("PowerState").asString())
                .isEqualTo("On");
        server.verify();
    }

    @Test
    @DisplayName("POST — Content-Type: application/json, Task 는 Location 헤더 우선 · 없으면 본문 @odata.id · 둘 다 없으면 empty")
    void postForTask() {
        // MockRestServiceServer 는 첫 요청 뒤 기대 추가를 금지한다 — 기대 3 건을 선선언하고 같은 순서로 호출한다.
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(java.net.URI.create("https://10.0.0.9/redfish/v1/TaskService/Tasks/1"));
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self/Actions/ComputerSystem.Reset"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.ACCEPTED).headers(headers));
        server.expect(requestTo("https://10.0.0.9/p")).andRespond(withStatus(HttpStatus.ACCEPTED)
                .body("{\"@odata.id\":\"/redfish/v1/TaskService/Tasks/2\"}").contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://10.0.0.9/q")).andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(client.postForTask("10.0.0.9", CREDS, "/redfish/v1/Systems/Self/Actions/ComputerSystem.Reset",
                Map.of("ResetType", "On"))).contains("/redfish/v1/TaskService/Tasks/1");
        assertThat(client.postForTask("10.0.0.9", CREDS, "/p", Map.of())).contains("/redfish/v1/TaskService/Tasks/2");
        assertThat(client.postForTask("10.0.0.9", CREDS, "/q", Map.of())).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("오류 분류 — 401 AUTH_FAILED · 412 PRECONDITION_FAILED · IO CONNECT_FAILED · 500 PROTOCOL")
    void classify() {
        server.expect(requestTo("https://10.0.0.9/a")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("https://10.0.0.9/b")).andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));
        server.expect(requestTo("https://10.0.0.9/c")).andRespond(withException(new IOException("refused")));
        server.expect(requestTo("https://10.0.0.9/d")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getJson("10.0.0.9", CREDS, "/a"))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.AUTH_FAILED));
        assertThatThrownBy(() -> client.getJson("10.0.0.9", CREDS, "/b"))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.PRECONDITION_FAILED));
        assertThatThrownBy(() -> client.getJson("10.0.0.9", CREDS, "/c"))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.CONNECT_FAILED));
        assertThatThrownBy(() -> client.getJson("10.0.0.9", CREDS, "/d"))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.PROTOCOL));
        server.verify();
    }

    @Test
    @DisplayName("getForResource — 본문과 ETag 헤더를 한 벌로 돌려준다 (PATCH 의 If-Match 재료)")
    void getForResource_capturesEtag() {
        org.springframework.http.HttpHeaders withEtag = new org.springframework.http.HttpHeaders();
        withEtag.setETag("W/\"1000\"");
        server.expect(requestTo("https://10.0.0.9/redfish/v1/AccountService/Accounts/2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"UserName\":\"admin\"}", MediaType.APPLICATION_JSON).headers(withEtag));

        RedfishResource resource = client.getForResource("10.0.0.9", CREDS, "/redfish/v1/AccountService/Accounts/2");

        assertThat(resource.body().path("UserName").asString()).isEqualTo("admin");
        assertThat(resource.etag()).isEqualTo("W/\"1000\"");
        server.verify();
    }

    @Test
    @DisplayName("patchJson — If-Match 헤더 · JSON 바디로 PATCH, 204 를 소화한다")
    void patchJson_sendsIfMatch() {
        server.expect(requestTo("https://10.0.0.9/redfish/v1/AccountService/Accounts/2"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "W/\"1000\""))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.patchJson("10.0.0.9", CREDS, "/redfish/v1/AccountService/Accounts/2", "W/\"1000\"",
                Map.of("Password", "new-standard"));
        server.verify();
    }

    @Test
    @DisplayName("patchJson — 412 는 PRECONDITION_FAILED 로 분류된다 (낡은 ETag · 동시 경합)")
    void patchJson_staleEtag_classified() {
        server.expect(requestTo("https://10.0.0.9/redfish/v1/AccountService/Accounts/2"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        assertThatThrownBy(() -> client.patchJson("10.0.0.9", CREDS,
                "/redfish/v1/AccountService/Accounts/2", "W/\"999\"", Map.of("Password", "x")))
                .isInstanceOf(RedfishRequestException.class)
                .extracting(e -> ((RedfishRequestException) e).getError())
                .isEqualTo(RedfishError.PRECONDITION_FAILED);
    }

    @Test
    @DisplayName("오류 분류 — 4xx 거절은 상태코드와 Redfish 오류 메시지를 함께 싣는다(2026-08-27 실기: 400 값 불허를 로그로 못 봤다)")
    void classify_rejection_carriesStatusAndRedfishMessage() {
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self/Bios/SD"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"@Message.ExtendedInfo":[{"MessageId":"Base.1.12.PropertyValueNotInList",
                         "Message":"The value 'Disabled' for the property Whitley0000 is not in the list of acceptable values."}],
                         "code":"Base.1.12.PropertyValueNotInList","message":"generic"}}"""));
        server.expect(requestTo("https://10.0.0.9/plain")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("not json"));

        assertThatThrownBy(() -> client.patchJson("10.0.0.9", CREDS, "/redfish/v1/Systems/Self/Bios/SD", "*",
                Map.of("Attributes", Map.of("Whitley0000", "Disabled"))))
                .isInstanceOfSatisfying(RedfishRequestException.class, e -> {
                    assertThat(e.getError()).isEqualTo(RedfishError.PROTOCOL);
                    assertThat(e.getMessage()).contains("400", "Whitley0000", "not in the list");
                });
        assertThatThrownBy(() -> client.getJson("10.0.0.9", CREDS, "/plain"))
                .hasMessageContaining("503")
                .hasMessageNotContaining("not json");
        server.verify();
    }

    @Test
    @DisplayName("patchJsonRefreshingEtag — If-Match:* 로 한 번에 받아들여지면 GET 이 없다(E2.5 사다리)")
    void etagLadder_starAccepted() {
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "*"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.patchJsonRefreshingEtag("10.0.0.9", CREDS, "/redfish/v1/Systems/Self",
                "/redfish/v1/Systems/Self", Map.of("Boot", Map.of("BootSourceOverrideEnabled", "Once")));
        server.verify();
    }

    @Test
    @DisplayName("patchJsonRefreshingEtag — 412 면 ETag 원천을 GET 해 fresh ETag 로 한 번 더 쓴다")
    void etagLadder_412RefreshesEtag() {
        HttpHeaders etag = new HttpHeaders();
        etag.setETag("W/\"fresh\"");
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "*"))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON).headers(etag));
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "W/\"fresh\""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.patchJsonRefreshingEtag("10.0.0.9", CREDS, "/redfish/v1/Systems/Self",
                "/redfish/v1/Systems/Self", Map.of("Boot", Map.of("BootSourceOverrideEnabled", "Once")));
        server.verify();
    }

    @Test
    @DisplayName("patchJsonRefreshingEtag — fresh ETag 로도 412 면 두 번째 예외를 올리고, 412 밖 거절은 재시도 없이 그대로")
    void etagLadder_propagation() {
        HttpHeaders etag = new HttpHeaders();
        etag.setETag("W/\"fresh\"");
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON).headers(etag));
        server.expect(requestTo("https://10.0.0.9/redfish/v1/Systems/Self"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));
        server.expect(requestTo("https://10.0.0.9/x"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.patchJsonRefreshingEtag("10.0.0.9", CREDS,
                "/redfish/v1/Systems/Self", "/redfish/v1/Systems/Self", Map.of()))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.PRECONDITION_FAILED));
        assertThatThrownBy(() -> client.patchJsonRefreshingEtag("10.0.0.9", CREDS, "/x", "/x", Map.of()))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.PROTOCOL));
        server.verify();
    }
}
