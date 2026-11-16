package com.example.serverprovision.global.security.springsecurity.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 사용자 관리 동작이 현재 상태와 충돌(409). 화면은 같은 판정({@code UserAction.blockReason})으로 버튼을 먼저 막으므로
 * direct POST · stale 화면 · 동시 요청(행위자 권한이 그새 바뀜)에서만 발동하는 안전망이다. 메시지는 화면 tooltip 과 같은 문장이다.
 */
public class UserManagementBlockedException extends ConflictException {

	public UserManagementBlockedException(String reason) {
		super(reason);
	}
}
