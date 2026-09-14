package com.example.serverprovision.global.security.springsecurity.dto.request;

import com.example.serverprovision.global.security.springsecurity.domain.Role;

import java.util.List;

public record SignUpRequest(
		String username,
		String password,
		String retypedPassword,
		String name,
		List<Role> roles
) {
}
