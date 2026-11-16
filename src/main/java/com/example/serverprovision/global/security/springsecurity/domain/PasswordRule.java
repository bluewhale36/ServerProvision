package com.example.serverprovision.global.security.springsecurity.domain;

/**
 * 비밀번호 규칙(S18 Q4 · Q5 — 사용자 확정): 영문 대소문자 · 숫자 · 특수기호 {@code ! @ # $ % ^ & * ( ) - _ = + . , ?} 로 8자 이상 72자 이하.
 * ASCII 만 허용하므로 글자 수와 바이트 수가 같아 bcrypt 의 72 바이트 한계와 일치한다. 빈 값은 {@code @NotBlank} 가 따로 잡으므로
 * 패턴은 빈 값에 침묵한다({@code ^$|}) — 한 상태에 메시지 하나(D-4).
 */
public final class PasswordRule {

	public static final String REGEX = "^$|^[A-Za-z0-9!@#$%^&*()\\-_=+.,?]{8,72}$";
	public static final String MESSAGE = "비밀번호는 영문 대소문자 · 숫자 · 특수기호(! @ # $ % ^ & * ( ) - _ = + . , ?)로 8자 이상 72자 이하로 입력하십시오.";

	/*
	 * HF29 — 비밀번호 칸의 상황별 문구 한 벌. 최초 관리자 등록 · 사용자 추가(SignUpRequest) · 초기 비밀번호 지정(InitialPasswordRequest) ·
	 * 서비스 안전망(PasswordMismatchException) · 화면의 제출 전 확인이 같은 문장을 쓴다. 라벨('비밀번호' · '초기 비밀번호')과 무관한
	 * 공통형이다. 이름은 상황 기준이라 MessageSource 키(S16-2)로 옮길 때 그대로 키가 된다. 본인 변경 화면('새 비밀번호')은 다른 개념이라 따로다.
	 */
	public static final String BLANK = "비밀번호를 입력하십시오.";
	public static final String RETYPE_BLANK = "비밀번호를 한 번 더 입력하십시오.";
	public static final String MISMATCH = "비밀번호가 일치하지 않습니다.";

	private PasswordRule() {
	}

	static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
