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

	@Override
	public boolean isAccountNonLocked() {
		return user.getIsLocked() != null && !user.getIsLocked();
	}

	@Override
	public boolean isEnabled() {
		return user.getIsEnabled() != null && user.getIsEnabled();
	}
}
