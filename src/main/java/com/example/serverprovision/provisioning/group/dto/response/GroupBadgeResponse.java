package com.example.serverprovision.provisioning.group.dto.response;

/**
 * 서버 목록 행에 붙는 소속 그룹 표시 (U3-4).
 *
 * <p>서버 목록의 조회 서비스는 execution 이고 그룹은 provisioning 이라, 요약 응답에 그룹을 실을 수 없다
 * (DEC-C — 실으면 execution → provisioning 역방향이 된다). 그래서 컨트롤러가 두 서비스를 각각 부른 뒤
 * {@code 서버 id → 이 응답} 의 map 으로 모델에 얹고, 행 조각이 그것을 읽는다.</p>
 */
public record GroupBadgeResponse(Long id, String name) {
}
