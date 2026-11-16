package com.example.serverprovision.global.exception;

/**
 * 기본 {@code /error} 경로로 넘어가는 거절에 실을 사람용 사유(HF29). 예외를 거치지 않는 거절(Spring Security 403 등)은 advice 를
 * 타지 않아 화면에 사유를 실을 길이 없었다 — 거절한 쪽이 이 요청 속성에 문장을 두면 {@link ProvisionErrorAttributes} 가
 * {@code message} 로 옮겨 {@code error.html} 과 JSON 응답 모두에 실린다.
 */
public final class ErrorReason {

	public static final String REQUEST_ATTRIBUTE = ErrorReason.class.getName() + ".message";

	private ErrorReason() {
	}
}
