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
    static final String PENDING_PATH = "/redfish/v1/Systems/Self/Bios/SD";
    static final String IF_MATCH_ANY = "*";

    private final RedfishClient redfishClient;
    private final BmcCredentialsFallback credentialsFallback;

    /** 현재 BIOS 설정 전문 + ETag — readback 대조와 412 폴백의 재료. */
    public RedfishResource bios(RedfishTarget target) {
        return credentialsFallback.attempt(target, c -> redfishClient.getForResource(target.bmcIp(), c, BIOS_PATH));
    }

    /**
     * 목표 속성을 pending 에 쓴다 — 먼저 {@code If-Match: *}, 412 면 fresh ETag 로 한 번 더(MAAS 선례 · 실측).
     * 그래도 거절되면 예외를 그대로 올린다 — 호출자가 PATCH_REJECTED 로 닫는다.
     */
    public void patchPending(RedfishTarget target, Map<String, Object> attributes) {
        Map<String, Object> body = Map.of("Attributes", attributes);
        try {
            credentialsFallback.attempt(target, c -> {
                redfishClient.patchJson(target.bmcIp(), c, PENDING_PATH, IF_MATCH_ANY, body);
                return null;
            });
        } catch (RedfishRequestException first) {
            if (first.getError() != RedfishError.PRECONDITION_FAILED) {
                throw first;
            }
            String etag = bios(target).etag();
            credentialsFallback.attempt(target, c -> {
                redfishClient.patchJson(target.bmcIp(), c, PENDING_PATH, etag, body);
                return null;
            });
        }
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
