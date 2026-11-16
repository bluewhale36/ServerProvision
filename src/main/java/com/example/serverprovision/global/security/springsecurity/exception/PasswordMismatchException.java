package com.example.serverprovision.global.security.springsecurity.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.global.security.springsecurity.domain.PasswordRule;

/** 비밀번호 대조 실패(400 · 필드 직결) — 확인 칸 불일치 또는 현재 비밀번호 오입력. 어느 칸인지는 예외가 들고 온다. */
public class PasswordMismatchException extends FieldBoundBadRequestException {

	private PasswordMismatchException(String message, String fieldName) {
		super(message, fieldName);
	}

	public static PasswordMismatchException retyped() {
		return new PasswordMismatchException(PasswordRule.MISMATCH, "retypedPassword");   // HF29 — 폼 검증과 같은 문장
	}

	public static PasswordMismatchException current() {
		return new PasswordMismatchException("현재 비밀번호가 올바르지 않습니다.", "currentPassword");
	}
}
