package com.example.serverprovision.global.security.springsecurity.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Role {

	ADMIN("ROLE_ADMIN", "관리자"),
    USER("ROLE_USER", "사용자");

	private final String roleName;
	private final String description;
}
