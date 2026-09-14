package com.example.serverprovision.global.security.springsecurity.domain;

/**
 * 비밀번호 규칙(S18 Q4 · Q5 — 사용자 확정): 영문 대소문자 · 숫자 · 특수기호 {@code ! @ # $ % ^ & * ( ) - _ = + . , ?} 로 8자 이상 72자 이하.
 * ASCII 만 허용하므로 글자 수와 바이트 수가 같아 bcrypt 의 72 바이트 한계와 일치한다. 빈 값은 {@code @NotBlank} 가 따로 잡으므로
 * 패턴은 빈 값에 침묵한다({@code ^$|}) — 한 상태에 메시지 하나(D-4).
 */
public final class PasswordRule {

	public static final String REGEX = "^$|^[A-Za-z0-9!@#$%^&*()\\-_=+.,?]{8,72}$";
	public static final String MESSAGE = "비밀번호는 영문 대소문자 · 숫자 · 특수기호(! @ # $ % ^ & * ( ) - _ = + . , ?)로 8자 이상 72자 이하로 입력하십시오.";

	private PasswordRule() {
	}

	static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
