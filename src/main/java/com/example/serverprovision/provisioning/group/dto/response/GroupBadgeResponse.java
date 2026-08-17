package com.example.serverprovision.provisioning.group.dto.response;

/**
 * 서버 목록 행에 붙는 소속 그룹 표시 (U3-4).
 *
 * <p>서버 목록의 조회 서비스는 execution 이고 그룹은 provisioning 이라, 요약 응답에 그룹을 실을 수 없다
 * (DEC-C — 실으면 execution → provisioning 역방향이 된다). 그래서 컨트롤러가 두 서비스를 각각 부른 뒤
 * {@code 서버 id → 이 응답} 의 map 으로 모델에 얹고, 행 조각이 그것을 읽는다.</p>
 */
public record GroupBadgeResponse(
        Long id,
        String name,
        /**
         * 그 그룹의 표준 세팅 정의서 id — 없으면 null (U3-5-d).
         *
         * <p>서버 상세가 "이 서버가 속한 그룹은 이 정의서를 표준으로 둔다" 를 알리는 데 쓴다. 배지를 만들
         * 때 이미 그룹 엔티티를 손에 들고 있으므로 함께 실어도 질의가 늘지 않는다. 이름 해석은 여기서
         * 하지 않는다 — 그것은 {@code setting} 의 일이고 컨트롤러가 잇는다.</p>
         */
        Long standardDefinitionId
) {
}
