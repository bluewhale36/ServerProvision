package com.example.serverprovision.global.redfish;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Redfish 계정 조작(E1.6) — 실측 계약(E0-4-1): 계정 GET 으로 fresh ETag 를 채집한 뒤
 * {@code If-Match} PATCH 로 비밀번호를 바꾼다(204). 어떤 자격으로 시도할지는 호출자가 정한다 —
 * 계정 표준화 사다리는 자격의 판별이 목적이라 폴백 · 캐시를 우회하고 명시 자격으로 부른다(D-2).
 */
@Component
@RequiredArgsConstructor
public class RedfishAccountService {

    private static final String ACCOUNTS_PATH = "/redfish/v1/AccountService/Accounts";

    private final RedfishClient redfishClient;

    /** 계정 컬렉션 전문 — 자격 유효성 탐침을 겸한다(401 이면 AUTH_FAILED 로 분류되어 던져진다). */
    public JsonNode accounts(String bmcIp, BmcCredentials credentials) {
        return redfishClient.getJson(bmcIp, credentials, ACCOUNTS_PATH);
    }

    /**
     * {@code username} 계정의 비밀번호 교체. 계정 URI 는 컬렉션에서 UserName 매칭으로 찾는다 —
     * 숫자 id 는 벤더 · 펌웨어마다 달라 경로를 하드코딩할 수 없다.
     */
    public void changePassword(String bmcIp, BmcCredentials current, String username, String newPassword) {
        for (JsonNode member : accounts(bmcIp, current).path("Members")) {
            String uri = member.path("@odata.id").asString(null);
            if (uri == null) {
                continue;
            }
            RedfishResource account = redfishClient.getForResource(bmcIp, current, uri);
            if (!username.equals(account.body().path("UserName").asString(null))) {
                continue;
            }
            redfishClient.patchJson(bmcIp, current, uri, account.etag(), Map.of("Password", newPassword));
            return;
        }
        throw new RedfishRequestException(RedfishError.NOT_FOUND,
                "PATCH " + ACCOUNTS_PATH + " — 계정 '" + username + "' 을 찾지 못했습니다", null);
    }
}
