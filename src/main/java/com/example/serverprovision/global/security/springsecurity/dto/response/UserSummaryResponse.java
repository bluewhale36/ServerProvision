package com.example.serverprovision.global.security.springsecurity.dto.response;

import com.example.serverprovision.global.security.springsecurity.domain.Role;

/**
 * 사용자 한 명의 요약(MA8) — 목록 행이자 다른 도메인(프로비저닝 집행인 기록)이 쓰는 공개 조회 결과. 엔티티를 밖에 내지 않는다.
 * hard delete 가 없으므로 기록이 참조한 id 는 비활성 계정이어도 계속 해석된다.
 *
 * @param level 가진 권한 중 가장 높은 등급
 */
public record UserSummaryResponse(Long id, String username, String name, Role level, boolean enabled,
                                  boolean mustChangePassword, java.time.LocalDateTime createdAt) {
}
