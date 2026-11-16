package com.example.serverprovision.global.security.springsecurity.domain;

import com.example.serverprovision.global.security.springsecurity.entity.Users;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class UserDetails_Impl implements UserDetails {

	private final Users user;
	private final List<Role> roles;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return roles.stream()
				.map(Role::getRoleName)
				.map(SimpleGrantedAuthority::new)
				.toList();
	}

	@Override
	public @Nullable String getPassword() {
		return user.getPassword();
	}

	@Override
	public String getUsername() {
		return user.getUsername();
	}

	public String displayName() {
		return user.getName();
	}

	/** MA8 — 사용자 관리(사이드바 링크 · /users) 대상인가. */
	public boolean isSuperAdmin() {
		return roles.contains(Role.SUPER_ADMIN);
	}

	/** S18 — 첫 로그인 강제 변경 여부(성공 핸들러 · 인터셉터가 읽는다). */
	public boolean mustChangePassword() {
		return user.isMustChangePassword();
	}

	@Override
	public boolean isAccountNonLocked() {
		return user.getIsLocked() != null && !user.getIsLocked();
	}

	@Override
	public boolean isEnabled() {
		return user.getIsEnabled() != null && user.getIsEnabled();
	}

	/**
	 * 사용자명으로 같은 사람을 판정한다(MA8) — 세션 등록소가 principal 을 키로 쓰므로, 로그인마다 새로 만들어지는 principal 이
	 * 같은 사용자로 묶여야 한다(Spring 의 {@code User} 와 같은 규칙).
	 */
	@Override
	public boolean equals(Object o) {
		return o instanceof UserDetails_Impl other && getUsername().equals(other.getUsername());
	}

	@Override
	public int hashCode() {
		return getUsername().hashCode();
	}
}
