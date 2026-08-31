package com.example.serverprovision.global.redfish;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

/**
 * Redfish 저수준 호출 (E1.5) — Basic 인증 · 자가서명 TLS(전용 {@code redfishRestClient}) · 오류 분류까지만 안다.
 * 어떤 리소스를 왜 부르는지는 {@link RedfishPowerService} 같은 상위가 정한다. 인증은 실측 결론대로
 * <b>Basic 우선</b>(세션은 30초 상한이라 Task 추적 같은 단기 용도에만 후속 슬라이스가 얹는다).
 */
@Component
public class RedfishClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    /** BMC HTTPS 포트 — 실장비는 443. 모조 하네스(CP5)나 특수망은 설정으로 바꾼다(443 이면 URL 에 생략). */
    private final int port;

    public RedfishClient(@Qualifier("redfishRestClient") RestClient restClient, ObjectMapper objectMapper,
                         @org.springframework.beans.factory.annotation.Value("${provision.bmc.port:443}") int port) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    /** GET → JSON. 실패는 {@link RedfishRequestException} 으로 분류(401 = AUTH_FAILED → 호출자가 다음 자격증명 폴백). */
    public JsonNode getJson(String bmcIp, BmcCredentials credentials, String path) {
        try {
            String body = restClient.get()
                    .uri(url(bmcIp, path))
                    .headers(headers -> headers.setBasicAuth(credentials.username(), credentials.password()))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw classify(e, "GET " + path);
        }
    }

    /** GET → 본문 + ETag 헤더 — 후속 PATCH 의 If-Match 재료(fresh 필수 · 재사용 시 412, E0-4-1 실측). */
    public RedfishResource getForResource(String bmcIp, BmcCredentials credentials, String path) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(url(bmcIp, path))
                    .headers(headers -> headers.setBasicAuth(credentials.username(), credentials.password()))
                    .retrieve()
                    .toEntity(String.class);
            String body = response.getBody();
            return new RedfishResource(
                    objectMapper.readTree(body == null ? "{}" : body),
                    response.getHeaders().getETag());
        } catch (Exception e) {
            throw classify(e, "GET " + path);
        }
    }

    /** PATCH(JSON) + If-Match — 계정 비밀번호 변경 등 쓰기 계약(E0-4-1: fresh ETag → 204). */
    public void patchJson(String bmcIp, BmcCredentials credentials, String path, String etag, Map<String, ?> body) {
        try {
            restClient.patch()
                    .uri(url(bmcIp, path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBasicAuth(credentials.username(), credentials.password());
                        if (etag != null) {
                            headers.setIfMatch(etag);
                        }
                    })
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw classify(e, "PATCH " + path);
        }
    }

    /**
     * PATCH 의 If-Match 사다리 — {@code *} 를 먼저 쓰고 <b>412 일 때만</b> {@code etagSourcePath} 의 fresh ETag 로
     * 한 번 더(E0-4 실측 · MAAS 선례). E3-1 {@code RedfishBiosService} 의 사다리를 두 번째 사용처(E2.5 boot override)가
     * 생겨 여기로 끌어올렸다. 412 밖의 거절과 두 번째 412 는 그대로 올린다 — 무한 재시도 없음.
     */
    public void patchJsonRefreshingEtag(String bmcIp, BmcCredentials credentials, String patchPath,
                                        String etagSourcePath, Map<String, ?> body) {
        try {
            patchJson(bmcIp, credentials, patchPath, "*", body);
        } catch (RedfishRequestException first) {
            if (first.getError() != RedfishError.PRECONDITION_FAILED) {
                throw first;
            }
            String etag = getForResource(bmcIp, credentials, etagSourcePath).etag();
            patchJson(bmcIp, credentials, patchPath, etag, body);
        }
    }

    /**
     * POST(JSON) → Task 경로. 실측(E0-4)의 SimpleUpdate · Reset 은 202 와 함께 Task 리소스를 준다 —
     * {@code Location} 헤더 또는 본문 {@code @odata.id} 에서 읽고, 어느 쪽에도 없으면 empty(2xx 면 전달 자체는 성공).
     */
    public Optional<String> postForTask(String bmcIp, BmcCredentials credentials, String path, Map<String, ?> body) {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(url(bmcIp, path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(credentials.username(), credentials.password()))
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toEntity(String.class);
            HttpHeaders headers = response.getHeaders();
            if (headers.getLocation() != null) {
                return Optional.of(headers.getLocation().getPath());
            }
            String responseBody = response.getBody();
            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode node = objectMapper.readTree(responseBody);
                if (node.hasNonNull("@odata.id")) {
                    return Optional.of(node.get("@odata.id").asString());
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            throw classify(e, "POST " + path);
        }
    }

    private String url(String bmcIp, String path) {
        return "https://" + bmcIp + (port == 443 ? "" : ":" + port) + path;
    }

    private RedfishRequestException classify(Exception e, String what) {
        if (e instanceof RedfishRequestException already) {
            return already;
        }
        if (e instanceof ResourceAccessException) {
            return new RedfishRequestException(RedfishError.CONNECT_FAILED, what + " — BMC 에 연결하지 못했습니다", e);
        }
        if (e instanceof HttpClientErrorException.Unauthorized) {
            return new RedfishRequestException(RedfishError.AUTH_FAILED, what + " — 자격증명 거부(401)", e);
        }
        if (e instanceof HttpClientErrorException client && client.getStatusCode().value() == 412) {
            return new RedfishRequestException(RedfishError.PRECONDITION_FAILED, what + " — ETag 선행 조건 불일치(412)", e);
        }
        if (e instanceof HttpClientErrorException.NotFound) {
            return new RedfishRequestException(RedfishError.NOT_FOUND, what + " — 리소스 부재(404)", e);
        }
        if (e instanceof HttpStatusCodeException http) {
            return new RedfishRequestException(RedfishError.PROTOCOL,
                    what + " — BMC 가 요청을 거절했습니다(" + http.getStatusCode().value() + rejectionReason(http) + ")", e);
        }
        return new RedfishRequestException(RedfishError.PROTOCOL, what + " — 응답을 해석하지 못했습니다", e);
    }

    private static final int REASON_MAX_LENGTH = 300;

    /**
     * 거절 본문의 Redfish 표준 오류 메시지({@code error.@Message.ExtendedInfo[].Message}, 없으면 {@code error.message}).
     * 상태코드만으론 원인을 못 본다 — 2026-08-27 실기에서 400 의 "값이 허용 목록에 없음" 을 curl 로 다시 캐야 했다.
     */
    private String rejectionReason(HttpStatusCodeException http) {
        String body = http.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            StringBuilder reason = new StringBuilder();
            for (JsonNode info : error.path("@Message.ExtendedInfo")) {
                String message = info.path("Message").asString("");
                if (!message.isBlank()) {
                    reason.append(reason.isEmpty() ? "" : " / ").append(message);
                }
            }
            if (reason.isEmpty()) {
                reason.append(error.path("message").asString(""));
            }
            if (reason.isEmpty()) {
                return "";
            }
            String text = reason.length() > REASON_MAX_LENGTH ? reason.substring(0, REASON_MAX_LENGTH) + "…" : reason.toString();
            return " · " + text;
        } catch (RuntimeException notJson) {
            return "";
        }
    }
}
