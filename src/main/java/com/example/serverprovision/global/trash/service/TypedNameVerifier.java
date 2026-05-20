package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.marker.ResourceType;

/**
 * S5-2-4 v3-1 — typed-name 검증 응집점. 사용자 진입 2 경로 (USER_DIRECT / NUDGE_REPLACE) 가
 * PurgeExecutor 호출 전에 본 단계를 통과한다.
 *
 * <p><strong>중복 방지</strong> : 휴지통 5 페이지의 controller / 휴지통 페이지 / 6 NudgeService 가
 * 동일 검증식을 각자 갖지 않도록 단일 컴포넌트로 응집 — {@link com.example.serverprovision.global.marker.Markable#displayName()}
 * 다형성을 통한 단일 진실 source.</p>
 *
 * <p>CP2 — 시그니처만. 본체는 CP4 에서 적절한 scanner SPI 활용 (활성 / trash / nudge 임시자원 중
 * 어디서 찾을지는 진입경로에 따라 다름).</p>
 */
public interface TypedNameVerifier {

    /**
     * 자원의 현재 {@code displayName()} 과 사용자가 입력한 typedName 이 일치하는지 검증.
     * 불일치 시 {@link com.example.serverprovision.global.exception.TypedNameMismatchException} throw.
     *
     * <p>자원 lookup 위치는 진입경로에 따라 다르다 :
     * <ul>
     *   <li>USER_DIRECT (5 list 페이지) — 활성 자원 (scanner.findActiveMarkableById)</li>
     *   <li>USER_DIRECT (휴지통 페이지) — 휴지통 자원 (scanner.findTrashedById)</li>
     *   <li>NUDGE_REPLACE — 충돌 대상 자원 (nudge 가 지목한 targetId 의 활성 자원)</li>
     * </ul>
     * 진입경로별 분기는 호출측이 처리 — 본 인터페이스는 단순히 활성 + 휴지통 양쪽을 시도하는 default 동작.
     */
    void verify(ResourceType resourceType, Long resourceId, String typedName);
}
