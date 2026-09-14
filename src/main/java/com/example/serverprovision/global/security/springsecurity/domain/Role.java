package com.example.serverprovision.global.security.springsecurity.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 접근 권한. 관리자는 일반 사용자 권한을 함께 보유한다(S18 · 사용자 결정 2026-09-14) — 저장 · 인가 어디서든
 * {@link #expand} 로 정규화해 ROLE_ADMIN 이 있으면 ROLE_USER 가 반드시 있게 한다.
 */
@RequiredArgsConstructor
@Getter
public enum Role {

	ADMIN("ROLE_ADMIN", "관리자") {
		@Override
		public Set<Role> implied() {
			return EnumSet.of(ADMIN, USER);
		}
	},
	USER("ROLE_USER", "사용자") {
		@Override
		public Set<Role> implied() {
			return EnumSet.of(USER);
		}
	};

	private final String roleName;
	private final String description;

	/** 이 권한이 함께 뜻하는 권한 집합(자기 자신 포함). */
	public abstract Set<Role> implied();

	/** 요청된 권한을 포함 관계로 닫는다 — null · 빈 입력은 빈 집합. 순서는 선언 순(ADMIN · USER). */
	public static Set<Role> expand(Collection<Role> requested) {
		EnumSet<Role> result = EnumSet.noneOf(Role.class);
		if (requested == null) {
			return result;
		}
		for (Role r : requested) {
			if (r != null) {
				result.addAll(r.implied());
			}
		}
		return result;
	}
}
