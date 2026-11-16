package com.example.serverprovision.global.security.springsecurity.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/** 사용자 관리 경로의 id 가 가리키는 계정이 없다(404) — 지운 계정이 없으므로(hard delete 없음) 잘못된 링크 · 위조 id 에서만 난다. */
public class UserNotFoundException extends NotFoundException {

	public UserNotFoundException(Long id) {
		super("사용자를 찾을 수 없습니다. id=" + id);
	}
}
