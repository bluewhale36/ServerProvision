package com.example.serverprovision.global.security.springsecurity.audit;

import java.util.Set;

/**
 * 계정 자체가 바뀐 사건(S20) — 가입 · 비밀번호 변경. {@code UsersService} 가 트랜잭션 안에서 발행하고
 * {@link AuthenticationEventLogger} 가 로그로 남긴다. 비밀번호 · 해시는 싣지 않는다.
 *
 * @param actor 누가 했나 — 부트스트랩 가입은 익명("anonymous"), 관리자 생성 · 변경은 그 사용자명
 */
public record AccountAuditEvent(Kind kind, String username, String actor, String mode, Set<String> roles,
                                boolean mustChangePassword) {

    public enum Kind {
        CREATED, PASSWORD_CHANGED
    }

    public static AccountAuditEvent created(String username, String actor, String mode, Set<String> roles,
                                            boolean mustChangePassword) {
        return new AccountAuditEvent(Kind.CREATED, username, actor, mode, roles, mustChangePassword);
    }

    public static AccountAuditEvent passwordChanged(String username, String actor, boolean forced) {
        return new AccountAuditEvent(Kind.PASSWORD_CHANGED, username, actor, forced ? "FORCED" : "VOLUNTARY", Set.of(), false);
    }
}
