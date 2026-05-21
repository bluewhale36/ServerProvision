package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * Zip 압축 해제 전 — entry 합계 / 비율 / 갯수 임계 초과.
 *
 * <p>S3.2 (K18) — 사용자가 "더 작은 zip 으로 재시도" 라는 잘못된 의미 해석을 막기 위해
 * 413 (Payload Too Large) 가 아닌 415 (Unsupported Media Type) 로 응답한다.</p>
 */
public class ZipBombSuspectedException extends SecurityException {

	public ZipBombSuspectedException(String reason) {
		super("Zip 압축 해제 검사에서 위험 신호가 감지되었습니다 : " + reason);
	}

	@Override
	public HttpStatus httpStatus() {
		return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
	}
}
