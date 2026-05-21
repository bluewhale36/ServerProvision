package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * 경로 입력의 형식 자체가 위험할 때 (null byte / control character / 빈 입력 / {@code ..} 등).
 * 400 으로 매핑.
 */
public class PathTraversalException extends SecurityException {

	public PathTraversalException(String reason) {
		super("경로 입력이 안전하지 않습니다 : " + reason);
	}

	@Override
	public HttpStatus httpStatus() {
		return HttpStatus.BAD_REQUEST;
	}
}
