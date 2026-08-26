package com.example.serverprovision.global.bmcweb;

import com.example.serverprovision.global.redfish.BmcCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * AMI 웹 API 저수준 호출(E3-2 D-4 · D-5) — 세션 발급 · 헤더 3종 부착 · 바디 판독 · 만료 시 재로그인 1회.
 * 어떤 설정을 왜 쓰는지는 {@code BmcSettingItem} 이 정한다. Redfish 와 같은 BMC · 같은 자가서명 TLS 라
 * {@code redfishRestClient} 를 그대로 쓴다(이름은 유래를 남긴다).
 *
 * <p>실측 계약(E0-3 · HAR): 성공은 200 + 요청 에코, 인증 실패는 <b>바디</b> {@code cc:7}(상태코드가 아니다),
 * 데이터 거절은 {@code error + code}. 세션 TTL(약 10분)에 기대지 않고 {@code cc:7} 을 받은 자리에서
 * 같은 자격으로 다시 열어 그 호출을 한 번 더 한다.</p>
 */
@Slf4j
@Component
public class AmiWebClient {

    static final String SESSION_PATH = "/api/session";
    static final String HEADER_CSRF = "X-CSRFTOKEN";
    static final String HEADER_REQUESTED_WITH = "X-Requested-With";
    static final String XML_HTTP_REQUEST = "XMLHttpRequest";
    static final int CC_AUTH_FAILED = 7;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int port;

    public AmiWebClient(@Qualifier("redfishRestClient") RestClient restClient, ObjectMapper objectMapper,
                        @Value("${provision.bmc.port:443}") int port) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    /** 세션 발급 — form 로그인. 자격증명이 거부되면 AUTH_FAILED(폴백이 다음 후보로 넘어가는 신호). */
    public AmiWebSession login(String bmcIp, BmcCredentials credentials) {
        Response response = exchange(HttpMethod.POST, bmcIp, SESSION_PATH, null, MediaType.APPLICATION_FORM_URLENCODED,
                loginForm(credentials), "POST " + SESSION_PATH);
        JsonNode body = response.body();
        if (!body.hasNonNull("CSRFToken")) {
            throw new AmiWebRequestException(AmiWebError.AUTH_FAILED,
                    "POST " + SESSION_PATH + " — 자격증명 거부(CSRFToken 없음)", null, null);
        }
        return new AmiWebSession(bmcIp, credentials, body.get("CSRFToken").asString(), response.cookie());
    }

    /** 세션이 묶인 호출면 — 항목 코드는 이것만 본다. */
    public AmiWebApi bind(AmiWebSession session) {
        return new AmiWebApi() {
            @Override public JsonNode get(String path) { return call(session, HttpMethod.GET, path, null); }
            @Override public JsonNode put(String path, Object body) { return call(session, HttpMethod.PUT, path, body); }
            @Override public JsonNode post(String path, Object body) { return call(session, HttpMethod.POST, path, body); }
        };
    }

    /** 로그아웃 — 세션 슬롯 반납. 실패는 무해하므로 로그만 남긴다(D-5). */
    public void logout(AmiWebSession session) {
        try {
            exchange(HttpMethod.DELETE, session.bmcIp(), SESSION_PATH, session, null, null, "DELETE " + SESSION_PATH);
        } catch (AmiWebRequestException e) {
            log.info("[bmcweb] {} — 로그아웃 실패(무해) : {}", session.bmcIp(), e.getMessage());
        }
    }

    /** 세션 호출 — 인증 실패 한 번은 같은 자격으로 다시 열어 재시도한다(만료 흡수). 두 번째도 거부면 올린다. */
    private JsonNode call(AmiWebSession session, HttpMethod method, String path, Object body) {
        String what = method.name() + " " + path;
        try {
            return sessionCall(session, method, path, body, what).body();
        } catch (AmiWebRequestException first) {
            if (first.getError() != AmiWebError.AUTH_FAILED) {
                throw first;
            }
            log.info("[bmcweb] {} — 세션 거부({}), 재로그인 후 1회 재시도", session.bmcIp(), what);
            AmiWebSession renewed = login(session.bmcIp(), session.credentials());
            session.renew(renewed.csrfToken(), renewed.cookie());
            return sessionCall(session, method, path, body, what).body();
        }
    }

    private Response sessionCall(AmiWebSession session, HttpMethod method, String path, Object body, String what) {
        MediaType type = body == null ? null : MediaType.APPLICATION_JSON;
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        return exchange(method, session.bmcIp(), path, session, type, payload, what);
    }

    /**
     * 요청 한 번 — 상태코드와 바디를 함께 읽어 판독한다. {@code retrieve()} 는 4xx 에서 바디를 버리므로
     * {@code exchange} 로 받는다(AMI 는 401 에도 {@code cc:7} 바디를 준다).
     */
    private Response exchange(HttpMethod method, String bmcIp, String path, AmiWebSession session,
                              MediaType contentType, String payload, String what) {
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(url(bmcIp, path))
                    .headers(headers -> {
                        headers.set(HEADER_REQUESTED_WITH, XML_HTTP_REQUEST);
                        if (session != null) {
                            headers.set(HEADER_CSRF, session.csrfToken());
                            if (session.cookie() != null) {
                                headers.set(HttpHeaders.COOKIE, session.cookie());
                            }
                        }
                    });
            if (payload != null) {
                spec = spec.contentType(contentType);
                spec.body(payload);
            }
            return spec.exchange((request, response) -> {
                int status = response.getStatusCode().value();
                String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                String cookie = joinCookies(response.getHeaders().get(HttpHeaders.SET_COOKIE));
                return interpret(status, raw, cookie, what);
            }, false);
        } catch (AmiWebRequestException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new AmiWebRequestException(AmiWebError.CONNECT_FAILED, what + " — BMC 에 연결하지 못했습니다", null, e);
        } catch (Exception e) {
            throw new AmiWebRequestException(AmiWebError.PROTOCOL, what + " — 응답을 해석하지 못했습니다", null, e);
        }
    }

    /** 실측 두 실패 모양을 먼저 가려낸 뒤에야 성공이다 — 200 이어도 {@code cc:7} 이면 인증 실패다. */
    private Response interpret(int status, String raw, String cookie, String what) {
        JsonNode body = raw == null || raw.isBlank() ? objectMapper.createObjectNode() : parse(raw, what);
        if (body.hasNonNull("cc")) {
            int cc = body.get("cc").asInt();
            if (cc == CC_AUTH_FAILED) {
                throw new AmiWebRequestException(AmiWebError.AUTH_FAILED, what + " — 인증 거부(cc:7)", cc, null);
            }
            throw new AmiWebRequestException(AmiWebError.PROTOCOL, what + " — BMC 오류(cc:" + cc + ")", cc, null);
        }
        if (body.hasNonNull("error") && body.hasNonNull("code")) {
            int code = body.get("code").asInt();
            throw new AmiWebRequestException(AmiWebError.DATA_REJECTED,
                    what + " — BMC 가 데이터를 거절했습니다(" + body.get("error").asString() + ", code " + code + ")", code, null);
        }
        if (status == 401) {
            throw new AmiWebRequestException(AmiWebError.AUTH_FAILED, what + " — 인증 거부(401)", null, null);
        }
        if (status < 200 || status >= 300) {
            throw new AmiWebRequestException(AmiWebError.PROTOCOL, what + " — BMC 가 요청을 거절했습니다(" + status + ")", null, null);
        }
        return new Response(body, cookie);
    }

    private JsonNode parse(String raw, String what) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new AmiWebRequestException(AmiWebError.PROTOCOL, what + " — 응답이 JSON 이 아닙니다", null, e);
        }
    }

    private static String joinCookies(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) {
            return null;
        }
        return String.join("; ", setCookies.stream()
                .map(c -> c.split(";", 2)[0].trim())
                .filter(c -> !c.isBlank())
                .toList());
    }

    private static String loginForm(BmcCredentials credentials) {
        return "username=" + URLEncoder.encode(credentials.username(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(credentials.password(), StandardCharsets.UTF_8);
    }

    private String url(String bmcIp, String path) {
        return "https://" + bmcIp + (port == 443 ? "" : ":" + port) + path;
    }

    private record Response(JsonNode body, String cookie) {
        Optional<String> cookieOrEmpty() {
            return Optional.ofNullable(cookie);
        }
    }
}
