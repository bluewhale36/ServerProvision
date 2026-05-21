package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * S3.2 (K17) — Zip 검사 도중 발생한 IO 실패. zip bomb 의심 ({@link ZipBombSuspectedException}) 과 의미가
 * 다르므로 별도 분류한다. 디스크 풀 / 임시 파일 권한 / 파일시스템 장애 등 운영 환경 이슈로 발생하며,
 * 사용자에게는 "콘텐츠 위협" 이 아니라 "서버 측 일시적 문제" 로 안내해야 한다.
 *
 * <p>B2 — 보안 예외 계층 분리 작업으로 super-class 를 {@link SecurityException} 으로 이동.
 * 사용자 콘텐츠 위협이 아니라 운영 IO 오류이지만, 컨트롤러의 {@code catch (DomainException)} 에 흡수되지 않고
 * framework 의 {@code @ExceptionHandler} 매핑을 거쳐야 한다는 점은 다른 보안 예외와 동일하므로 같은 계층에 둔다.</p>
 *
 * <p>HTTP 매핑 — 500 (Internal Server Error). 다른 보안 예외와 다르게 운영 IO 실패이므로
 * 4xx 가 아닌 5xx 를 반환한다.</p>
 */
public class ZipBombInspectionFailedException extends SecurityException {

	public ZipBombInspectionFailedException(String reason) {
		super("zip 검사 도중 서버 측 IO 오류가 발생했습니다 : " + reason);
	}

	public ZipBombInspectionFailedException(String reason, Throwable cause) {
		super("zip 검사 도중 서버 측 IO 오류가 발생했습니다 : " + reason, cause);
	}

	@Override
	public HttpStatus httpStatus() {
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}
}
