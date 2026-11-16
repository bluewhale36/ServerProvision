package com.example.serverprovision.global.security.springsecurity.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 사용자 관리 경로의 id 가 가리키는 계정이 없다(404) — 지운 계정이 없으므로(hard delete 없음) 잘못된 링크 · 위조 id 에서만 난다.
 * 메시지는 화면에 그대로 나가므로 내부 id 를 싣지 않는다(HF29 · docs C-26).
 */
public class UserNotFoundException extends NotFoundException {

	public UserNotFoundException(Long id) {
		super("사용자를 찾을 수 없습니다. 목록에서 다시 고르십시오.");
	}
}
