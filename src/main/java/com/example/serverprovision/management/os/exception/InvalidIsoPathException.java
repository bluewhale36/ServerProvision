package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 사용자 입력 ISO 경로가 저장 경로로 쓸 수 없는 상태일 때 (예: '/' 로 끝나는데 업로드 파일이 없음) 던진다.
 * 사용자의 입력 자체에 문제가 있는 케이스이므로 409 로 매핑된다 — 서버 내부 오류(500)와 구분.
 */
public class InvalidIsoPathException extends ConflictException {

	public InvalidIsoPathException(String message) {
		super(message);
	}
}
