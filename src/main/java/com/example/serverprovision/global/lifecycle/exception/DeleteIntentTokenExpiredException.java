package com.example.serverprovision.global.lifecycle.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * MK3-2 (DCM3-2.6) — DeleteIntent token 의 TTL 초과 또는 1회 사용 후 재호출.
 *
 * <p>ApiExceptionHandler 가 410 Gone 으로 매핑. 클라이언트는 modal 을 닫고 새 softDelete 진입을 안내.</p>
 */
public class DeleteIntentTokenExpiredException extends DomainException {

	public DeleteIntentTokenExpiredException(String tokenAsString) {
		super("DeleteIntent token 이 만료되었거나 이미 사용되었습니다 : " + tokenAsString
					  + " · 다시 삭제 시도해주세요.");
	}
}
