package com.example.serverprovision.global.security.springsecurity.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 접근 권한. 높은 등급은 낮은 등급의 권한을 함께 보유한다(S18 · 사용자 결정 2026-09-14, MA8 에서 SUPER_ADMIN 추가) —
 * 저장 · 인가 어디서든 {@link #expand} 로 정규화해 상위 행이 있으면 하위 행이 반드시 있게 한다.
 *
 * <p>상수 선언 순서가 등급의 높은 순이다. {@link #expand} 의 결과 순회와 {@link #highest} 가 이 순서에 기댄다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum Role {
	SUPER_ADMIN("ROLE_SUPER_ADMIN", "최고 관리자") {
		@Override
		public Set<Role> implied() {
			return EnumSet.of(SUPER_ADMIN, ADMIN, USER);
		}
	},
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

	/** 요청된 권한을 포함 관계로 닫는다 — null · 빈 입력은 빈 집합. 순서는 선언 순(높은 등급부터). */
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

	/** 가진 권한 중 가장 높은 등급 — 화면의 등급 배지 · 등급 선택의 현재값. 비어 있으면 null. */
	public static Role highest(Collection<Role> roles) {
		Set<Role> expanded = expand(roles);
		return expanded.isEmpty() ? null : expanded.iterator().next();
	}
}
