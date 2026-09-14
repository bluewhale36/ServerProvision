package com.example.serverprovision.global.security.springsecurity.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 이미 있는 아이디로 가입(409 · username 필드 직결). 화면은 {@code UsersService.usernameTaken} 으로 먼저 막으므로
 * direct POST · 동시 가입 레이스에서만 발동하는 안전망이고, DB UNIQUE 가 최후 방어선이다.
 */
public class DuplicateUsernameException extends FieldBoundConflictException {

	public DuplicateUsernameException(String username) {
		super("이미 사용 중인 아이디입니다. username=" + username, "username");
	}
}
