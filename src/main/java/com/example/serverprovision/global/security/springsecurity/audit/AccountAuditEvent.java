package com.example.serverprovision.global.security.springsecurity.audit;

import org.slf4j.Logger;

import java.util.Set;

/**
 * 계정 자체가 바뀐 사건(S20 · MA8 확장) — 가입 · 비밀번호 변경 · 권한 등급 변경 · 비활성화 · 활성화 · 초기 비밀번호 지정.
 * 서비스가 트랜잭션 안에서 발행하고 {@link AuthenticationEventLogger} 가 로그로 남긴다. 비밀번호 · 해시는 싣지 않는다.
 *
 * <p>로그 문장은 {@link Kind} 상수가 소유한다 — 사건 종류가 늘 때 리스너의 분기가 함께 자라지 않게(MA8 D-11).</p>
 *
 * @param actor         누가 했나 — 부트스트랩 가입은 익명("anonymous"), 그 외는 로그인한 사용자명
 * @param roles         사건 뒤의 권한 행(권한 등급 변경은 바뀐 뒤)
 * @param previousRoles 권한 등급 변경 전의 권한 행 — 다른 사건은 빈 집합
 */
public record AccountAuditEvent(Kind kind, String username, String actor, String mode, Set<String> roles,
                                Set<String> previousRoles, boolean mustChangePassword) {

	public enum Kind {
		CREATED {
			@Override
			void log(Logger log, AccountAuditEvent e) {
				log.info("[auth.account.created] user={} by={} mode={} roles={} mustChangePassword={}",
						e.username(), e.actor(), e.mode(), e.roles(), e.mustChangePassword());
			}
		},
		PASSWORD_CHANGED {
			@Override
			void log(Logger log, AccountAuditEvent e) {
				log.info("[auth.password.changed] user={} by={} mode={}", e.username(), e.actor(), e.mode());
			}
		},
		ROLE_CHANGED {
			@Override
			void log(Logger log, AccountAuditEvent e) {
				log.info("[auth.account.role-changed] user={} by={} from={} to={}",
						e.username(), e.actor(), e.previousRoles(), e.roles());
			}
		},
		DISABLED {
			@Override
			void log(Logger log, AccountAuditEvent e) {
				log.info("[auth.account.disabled] user={} by={}", e.username(), e.actor());
			}
		},
		ENABLED {
			@Override
			void log(Logger log, AccountAuditEvent e) {
				log.info("[auth.account.enabled] user={} by={}", e.username(), e.actor());
			}
		},
		PASSWORD_ASSIGNED {
			@Override
			void log(Logger log, AccountAuditEvent e) {
				log.info("[auth.password.assigned] user={} by={}", e.username(), e.actor());
			}
		};

		abstract void log(Logger log, AccountAuditEvent e);
	}

	/** 이 사건 문장을 남긴다 — 리스너는 종류를 따지지 않고 이것만 부른다. */
	public void logTo(Logger log) {
		kind.log(log, this);
	}

	public static AccountAuditEvent created(String username, String actor, String mode, Set<String> roles,
	                                        boolean mustChangePassword) {
		return new AccountAuditEvent(Kind.CREATED, username, actor, mode, roles, Set.of(), mustChangePassword);
	}

	public static AccountAuditEvent passwordChanged(String username, String actor, boolean forced) {
		return new AccountAuditEvent(Kind.PASSWORD_CHANGED, username, actor, forced ? "FORCED" : "VOLUNTARY", Set.of(), Set.of(), false);
	}

	public static AccountAuditEvent roleChanged(String username, String actor, Set<String> from, Set<String> to) {
		return new AccountAuditEvent(Kind.ROLE_CHANGED, username, actor, "MANAGED", to, from, false);
	}

	public static AccountAuditEvent disabled(String username, String actor) {
		return new AccountAuditEvent(Kind.DISABLED, username, actor, "MANAGED", Set.of(), Set.of(), false);
	}

	public static AccountAuditEvent enabled(String username, String actor) {
		return new AccountAuditEvent(Kind.ENABLED, username, actor, "MANAGED", Set.of(), Set.of(), false);
	}

	public static AccountAuditEvent passwordAssigned(String username, String actor) {
		return new AccountAuditEvent(Kind.PASSWORD_ASSIGNED, username, actor, "MANAGED", Set.of(), Set.of(), true);
	}
}
