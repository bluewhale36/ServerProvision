package com.example.serverprovision.provisioning.domain.vo;

/**
 * Password 속성의 길이 제약 ({@code MinLength} / {@code MaxLength}).
 */
public record PasswordLength(int min, int max) {

	public boolean isValid(int length) {
		return length >= min && length <= max;
	}
}
