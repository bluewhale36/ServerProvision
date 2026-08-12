package com.example.serverprovision.provisioning.group.dto.response;

import java.time.LocalDateTime;

/**
 * 그룹 목록의 한 줄 (U3-4). 리포지토리가 집계 쿼리로 직접 만든다.
 *
 * <p>{@code memberCount} 가 {@code long} 인 것은 JPQL {@code count()} 의 반환 타입이기 때문이다 —
 * 화면에 그대로 찍히는 값이라 변환 계층을 하나 더 두지 않는다.</p>
 *
 * <p>{@code specDiverged} 는 멤버의 하드웨어 구성이 갈렸는가다. 목록에서도 알려주는 이유는
 * 그룹을 열어보기 전에 손볼 것이 있는지 알아야 하기 때문이다 — 자원 화면이 사용 중단을 점으로
 * 알리는 것과 같은 자리다. 집계 질의는 이 값을 모르므로 조회 서비스가 뒤에 채운다.</p>
 */
public record GroupSummaryResponse(
        Long id,
        String name,
        long memberCount,
        boolean specDiverged,
        LocalDateTime createdAt
) {
    /** JPQL 집계가 쓰는 생성자 — 혼재 여부는 조회 서비스가 {@link #withSpecDiverged} 로 채운다. */
    public GroupSummaryResponse(Long id, String name, long memberCount, LocalDateTime createdAt) {
        this(id, name, memberCount, false, createdAt);
    }

    public GroupSummaryResponse withSpecDiverged(boolean diverged) {
        return new GroupSummaryResponse(id, name, memberCount, diverged, createdAt);
    }
}
