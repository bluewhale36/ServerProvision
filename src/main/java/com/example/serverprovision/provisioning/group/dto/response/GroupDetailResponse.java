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
        /**
         * 이 그룹의 표준 세팅 정의서 id — 정하지 않았으면 null (U3-5-d).
         *
         * <p>id 만 싣고 이름 · 상태를 담지 않는 이유는 해석이 {@code setting} 의 일이기 때문이다.
         * 그룹 조회 서비스가 정의서를 직접 읽으면 {@code group → setting} 참조가 조회 경로에 생긴다.
         * 컨트롤러가 이 id 로 {@code SettingQueryService.resolveReference} 를 불러 화면 재료를 만든다
         * (U3-5-c 가 그룹과 할당을 컨트롤러에서 이은 것과 같은 형태).</p>
         */
        Long standardDefinitionId,
        List<GroupMemberResponse> members,
        boolean specDiverged,
        int candidateCount
) {
}
