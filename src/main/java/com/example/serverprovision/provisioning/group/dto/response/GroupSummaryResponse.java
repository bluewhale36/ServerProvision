package com.example.serverprovision.provisioning.group.dto.response;

import java.time.LocalDateTime;

/**
 * 그룹 목록의 한 줄 (U3-4 · S8-1 개정). 조회 서비스가 한 쪽의 엔티티에서 만든다.
 *
 * <p>{@code memberCount} 는 엔티티의 {@code @Formula} 파생 속성(long)을 그대로 싣는다(S8-1) —
 * 화면에 그대로 찍히는 값이라 변환 계층을 하나 더 두지 않는다.</p>
 *
 * <p>{@code specDiverged} 는 멤버의 하드웨어 구성이 갈렸는가다. 목록에서도 알려주는 이유는
 * 그룹을 열어보기 전에 손볼 것이 있는지 알아야 하기 때문이다 — 자원 화면이 사용 중단을 점으로
 * 알리는 것과 같은 자리다. 보이는 쪽의 그룹에 대해서만 조회 서비스가 판정해 채운다.</p>
 */
public record GroupSummaryResponse(
        Long id,
        String name,
        long memberCount,
        boolean specDiverged,
        LocalDateTime createdAt
) {
}
