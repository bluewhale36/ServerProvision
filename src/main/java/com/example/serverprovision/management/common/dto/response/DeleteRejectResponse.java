package com.example.serverprovision.management.common.dto.response;

import com.example.serverprovision.global.marker.ResourceType;

import java.util.List;

/**
 * MK3-2 (DCM3-2.2) — softDelete 사전조건 위반 시 응답 본문 (409 + JSON).
 *
 * <p>UI 가 본 payload 를 받아 reject modal 을 띄운다. {@code availableActions} 에 가능한 사용자 액션을
 * 명시해 클라이언트가 modal 의 버튼 분기를 결정.</p>
 *
 * @param code              상수 {@code "SOFTDELETE_REQUIRES_INTENT"} — 클라이언트 분기 키
 * @param resourceType      자원 종류
 * @param resourceId        자원 PK
 * @param missingPath       사전조건 검사 시점의 부재 경로
 * @param intentToken       DeleteIntentRegistry 발급 token (`del-<uuid>` 형식)
 * @param tokenTtlSeconds   token TTL 잔여 (default 300)
 * @param availableActions  사용자 modal 버튼 (예: ["CORRECT_PATH_THEN_DELETE", "FORCED_CLEAR"])
 * @param ghostCandidate    이미 ghost 상태인지 (도입 시점 잔존 ghost 식별)
 */
public record DeleteRejectResponse(
        String code,
        ResourceType resourceType,
        Long resourceId,
        String missingPath,
        String intentToken,
        long tokenTtlSeconds,
        List<String> availableActions,
        boolean ghostCandidate
) {

    public static final String CODE = "SOFTDELETE_REQUIRES_INTENT";
}
