package com.example.serverprovision.global.bmcweb;

import com.example.serverprovision.global.redfish.BmcCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * E3-2 D-4 · D-5 — 실측 계약을 HTTP 층에서 고정한다: form 로그인 → CSRF · 쿠키, 요청마다 헤더 3종, 성공은 에코,
 * 인증 실패는 바디 {@code cc:7}(상태코드 무관), 데이터 거절은 {@code error+code}, 만료 1회는 재로그인 후 재시도.
 */
class AmiWebClientTest {

    private static final String BMC = "10.10.0.51";
    private static final BmcCredentials CREDENTIALS = new BmcCredentials("admin", "standard-pw", "standard");

    private MockRestServiceServer server;
    private AmiWebClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AmiWebClient(builder.build(), new ObjectMapper(), 443);
    }

    @Test
    @DisplayName("login — form 으로 보내고 CSRFToken 과 Set-Cookie 를 세션에 담는다; 이후 호출에 헤더 3종")
    void loginThenCallCarriesHeaders() {
        expectLogin("tok-1", "QSESSIONID=s1");
        server.expect(requestTo("https://10.10.0.51/api/settings/date-time")).andExpect(method(HttpMethod.GET))
                .andExpect(header(AmiWebClient.HEADER_CSRF, "tok-1"))
                .andExpect(header("Cookie", "QSESSIONID=s1"))
                .andExpect(header(AmiWebClient.HEADER_REQUESTED_WITH, AmiWebClient.XML_HTTP_REQUEST))
                .andRespond(withSuccess("{\"id\":1,\"timezone\":\"Asia/Seoul\"}", MediaType.APPLICATION_JSON));

        AmiWebSession session = client.login(BMC, CREDENTIALS);
        JsonNode body = client.bind(session).get("/api/settings/date-time");

        assertThat(body.get("timezone").asString()).isEqualTo("Asia/Seoul");
        assertThat(session.toString()).doesNotContain("tok-1").doesNotContain("standard-pw");
        server.verify();
    }

    @Test
    @DisplayName("login — 자격증명 거부(cc:7 · 200)는 AUTH_FAILED — 폴백이 다음 후보로 넘어가는 신호")
    void loginRejectedIsAuthFailure() {
        server.expect(requestTo("https://10.10.0.51/api/session"))
                .andRespond(withSuccess("{\"cc\":7,\"error\":\"Invalid Authentication\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.login(BMC, CREDENTIALS))
                .isInstanceOfSatisfying(AmiWebRequestException.class, e -> {
                    assertThat(e.getError()).isEqualTo(AmiWebError.AUTH_FAILED);
                    assertThat(e.authFailure()).isTrue();
                });
    }

    @Test
    @DisplayName("쓰기 — 200 + 요청 에코가 성공이고, JSON 바디가 Content-Type application/json 으로 나간다")
    void putEchoesBody() {
        expectLogin("tok-1", "QSESSIONID=s1");
        server.expect(requestTo("https://10.10.0.51/api/cold_redundant-status")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(content().json("{\"master_psu\":0,\"set_cold_redundant_enable\":0}"))
                .andRespond(withSuccess("{\"master_psu\":0,\"set_cold_redundant_enable\":0}", MediaType.APPLICATION_JSON));

        JsonNode echo = client.bind(client.login(BMC, CREDENTIALS))
                .post("/api/cold_redundant-status", Map.of("master_psu", 0, "set_cold_redundant_enable", 0));

        assertThat(echo.get("set_cold_redundant_enable").asInt()).isZero();
        server.verify();
    }

    @Test
    @DisplayName("세션 만료(401 + cc:7) — 같은 자격으로 재로그인 한 번 뒤 그 호출을 다시 한다; 두 번째도 거부면 AUTH 로 올린다")
    void expiredSessionRelogsInOnce() {
        expectLogin("tok-1", "QSESSIONID=s1");
        server.expect(requestTo("https://10.10.0.51/api/settings/date-time")).andExpect(header(AmiWebClient.HEADER_CSRF, "tok-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"cc\":7,\"error\":\"Invalid Authentication\"}"));
        expectLogin("tok-2", "QSESSIONID=s2");
        server.expect(requestTo("https://10.10.0.51/api/settings/date-time")).andExpect(header(AmiWebClient.HEADER_CSRF, "tok-2"))
                .andExpect(header("Cookie", "QSESSIONID=s2"))
                .andRespond(withSuccess("{\"timezone\":\"Asia/Seoul\"}", MediaType.APPLICATION_JSON));

        AmiWebSession session = client.login(BMC, CREDENTIALS);
        JsonNode body = client.bind(session).get("/api/settings/date-time");
        assertThat(body.get("timezone").asString()).isEqualTo("Asia/Seoul");
        server.verify();

        // 두 번째 거부 — 재로그인 뒤에도 cc:7 이면 더 반복하지 않고 올린다
        server.reset();
        server.expect(requestTo("https://10.10.0.51/api/settings/fanprofile/mode"))
                .andRespond(withSuccess("{\"cc\":7}", MediaType.APPLICATION_JSON));
        expectLogin("tok-3", "QSESSIONID=s3");
        server.expect(requestTo("https://10.10.0.51/api/settings/fanprofile/mode"))
                .andRespond(withSuccess("{\"cc\":7}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.bind(session).get("/api/settings/fanprofile/mode"))
                .isInstanceOfSatisfying(AmiWebRequestException.class, e -> assertThat(e.getError()).isEqualTo(AmiWebError.AUTH_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("데이터 거절 {error, code} 는 DATA_REJECTED 에 code 를 싣고, 비 JSON · 5xx 는 PROTOCOL, 연결 실패는 CONNECT_FAILED")
    void failureClassification() {
        expectLogin("tok-1", "QSESSIONID=s1");
        server.expect(requestTo("https://10.10.0.51/api/settings/fanprofile"))
                .andRespond(withSuccess("{\"error\":\"Invalid Data\",\"code\":1010}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://10.10.0.51/api/settings/network-bond"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));
        server.expect(requestTo("https://10.10.0.51/api/settings/network-bond"))
                .andRespond(request -> { throw new ResourceAccessException("connection refused"); });
        AmiWebSession session = client.login(BMC, CREDENTIALS);
        AmiWebApi api = client.bind(session);

        assertThatThrownBy(() -> api.post("/api/settings/fanprofile", Map.of("strMode", "x")))
                .isInstanceOfSatisfying(AmiWebRequestException.class, e -> {
                    assertThat(e.getError()).isEqualTo(AmiWebError.DATA_REJECTED);
                    assertThat(e.getCode()).isEqualTo(1010);
                    assertThat(e.authFailure()).isFalse();
                });
        assertThatThrownBy(() -> api.get("/api/settings/network-bond"))
                .isInstanceOfSatisfying(AmiWebRequestException.class, e -> assertThat(e.getError()).isEqualTo(AmiWebError.PROTOCOL));
        assertThatThrownBy(() -> api.get("/api/settings/network-bond"))
                .isInstanceOfSatisfying(AmiWebRequestException.class, e -> assertThat(e.getError()).isEqualTo(AmiWebError.CONNECT_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("logout — DELETE /api/session, 실패해도 예외를 내지 않는다(무해)")
    void logoutIsBestEffort() {
        expectLogin("tok-1", "QSESSIONID=s1");
        server.expect(requestTo("https://10.10.0.51/api/session")).andExpect(method(HttpMethod.DELETE))
                .andExpect(header(AmiWebClient.HEADER_CSRF, "tok-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        AmiWebSession session = client.login(BMC, CREDENTIALS);

        client.logout(session);
        server.verify();
    }

    @Test
    @DisplayName("포트가 443 이 아니면 URL 에 붙는다(모의 하네스 8463)")
    void nonDefaultPortInUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer local = MockRestServiceServer.bindTo(builder).build();
        AmiWebClient mock = new AmiWebClient(builder.build(), new ObjectMapper(), 8463);
        local.expect(requestTo("https://127.0.0.1:8463/api/session"))
                .andRespond(withSuccess("{\"ok\":0,\"CSRFToken\":\"t\"}", MediaType.APPLICATION_JSON));

        assertThat(mock.login("127.0.0.1", CREDENTIALS).bmcIp()).isEqualTo("127.0.0.1");
        local.verify();
    }

    private void expectLogin(String token, String cookie) {
        server.expect(requestTo("https://10.10.0.51/api/session")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/x-www-form-urlencoded"))
                .andExpect(header(AmiWebClient.HEADER_REQUESTED_WITH, AmiWebClient.XML_HTTP_REQUEST))
                .andExpect(content().string("username=admin&password=standard-pw"))
                .andRespond(withSuccess("{\"ok\":0,\"privilege\":4,\"CSRFToken\":\"" + token + "\"}", MediaType.APPLICATION_JSON)
                        .header("Set-Cookie", cookie + "; Path=/; HttpOnly"));
    }
}
