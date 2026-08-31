package com.example.serverprovision.global.redfish;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

/**
 * Redfish BIOS 설정 호출(E3-1) — {@code Systems/Self/Bios}(현재값 · ETag) 와 {@code Bios/SD}(pending) 두 리소스만
 * 안다. 실측 계약(E0-4-3): PATCH 는 {@code If-Match: *} 로 204, pending 은 비어 있으면 GET 404 이지만 PATCH 는
 * 수락한다. 어느 값을 왜 쓰는지는 상위(setting step)가 정한다 — {@link RedfishUpdateService} 와 같은 자리.
 */
@Component
@RequiredArgsConstructor
public class RedfishBiosService {

    static final String BIOS_PATH = "/redfish/v1/Systems/Self/Bios";
    static final String REGISTRIES_PATH = "/redfish/v1/Registries/";
    static final String PENDING_PATH = "/redfish/v1/Systems/Self/Bios/SD";

    private final RedfishClient redfishClient;
    private final BmcCredentialsFallback credentialsFallback;

    /** 현재 BIOS 설정 전문 + ETag — readback 대조와 412 폴백의 재료. */
    public RedfishResource bios(RedfishTarget target) {
        return credentialsFallback.attempt(target, c -> redfishClient.getForResource(target.bmcIp(), c, BIOS_PATH));
    }

    /**
     * 목표 속성을 pending 에 쓴다 — If-Match 사다리({@code *} → 412 시 fresh ETag)는
     * {@link RedfishClient#patchJsonRefreshingEtag} 로 올라갔다(E2.5 에서 두 번째 사용처 발생).
     * 사다리 밖 거절은 그대로 올라온다 — 호출자가 PATCH_REJECTED 로 닫는다.
     */
    public void patchPending(RedfishTarget target, Map<String, Object> attributes) {
        Map<String, Object> body = Map.of("Attributes", attributes);
        credentialsFallback.attempt(target, c -> {
            redfishClient.patchJsonRefreshingEtag(target.bmcIp(), c, PENDING_PATH, BIOS_PATH, body);
            return null;
        });
    }

    /**
     * BIOS 속성 레지스트리 전문(E3-3) — 경로를 하드코딩하지 않고 {@code Bios.AttributeRegistry} 가 가리킨 이름으로
     * {@code Registries/{id}} 를 읽어 {@code Location[0].Uri} 를 따라간다(실측 체인: BiosAttributeRegistry →
     * /redfish/v1/Registries/BiosAttributeRegistry.json). 이름이나 Location 이 없으면 PROTOCOL 로 올린다.
     */
    public RedfishRegistry registry(RedfishTarget target) {
        String registryId = bios(target).body().path("AttributeRegistry").asString("");
        if (registryId.isBlank()) {
            throw new RedfishRequestException(RedfishError.PROTOCOL, "GET " + BIOS_PATH + " — AttributeRegistry 없음", null);
        }
        JsonNode index = credentialsFallback.attempt(target,
                c -> redfishClient.getJson(target.bmcIp(), c, REGISTRIES_PATH + registryId));
        String uri = index.path("Location").path(0).path("Uri").asString("");
        if (uri.isBlank()) {
            throw new RedfishRequestException(RedfishError.PROTOCOL,
                    "GET " + REGISTRIES_PATH + registryId + " — Location[0].Uri 없음", null);
        }
        JsonNode body = credentialsFallback.attempt(target, c -> redfishClient.getJson(target.bmcIp(), c, uri));
        return new RedfishRegistry(registryId, uri, body.toString());
    }

    /** pending 전문 — 비어 있으면(404) empty. 실패 판정이 아니라 관찰 기록의 재료다(E3-1 D-4 개정 2). */
    public Optional<JsonNode> pending(RedfishTarget target) {
        try {
            return Optional.of(credentialsFallback.attempt(target,
                    c -> redfishClient.getJson(target.bmcIp(), c, PENDING_PATH)));
        } catch (RedfishRequestException e) {
            if (e.getError() == RedfishError.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
