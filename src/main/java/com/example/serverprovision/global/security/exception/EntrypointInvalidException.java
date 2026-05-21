package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * {@code entrypointRelativePath} 입력이 정책 위반 (절대경로 / {@code ..} / null byte / 길이 / 트리 밖).
 * 400 으로 매핑.
 */
public class EntrypointInvalidException extends SecurityException {

	public EntrypointInvalidException(String reason) {
		super("진입점 경로가 안전하지 않습니다 : " + reason);
	}

	@Override
	public HttpStatus httpStatus() {
		return HttpStatus.BAD_REQUEST;
	}
}
