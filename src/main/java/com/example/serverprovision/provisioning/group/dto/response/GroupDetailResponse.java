package com.example.serverprovision.provisioning.group.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 그룹 상세 화면 한 판 (U3-4).
 *
 * <p>{@code candidateCount} 는 <b>넣을 수 있는 서버의 수</b>다. 목록 자체는 담지 않는다 —
 * 고르기는 모달에서 일어나고(개정), 모달을 열 때 조각 엔드포인트가 따로 내려준다.
 * 상세를 그릴 때마다 후보를 시간 × 스펙으로 조립하면 열지도 않을 화면의 값을 매번 치르게 된다.</p>
 *
 * <p>{@code specDiverged} 는 멤버의 구성이 갈렸는가다. <b>차단이 아니라 안내</b>이며(DEC-I),
 * 스펙은 그룹의 정체성이 아니므로 갈린 것 자체는 위반이 아니다.</p>
 */
public record GroupDetailResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        List<GroupMemberResponse> members,
        boolean specDiverged,
        int candidateCount
) {
}
