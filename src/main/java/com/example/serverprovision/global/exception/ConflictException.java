package com.example.serverprovision.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 현재 리소스 상태와 요청이 충돌할 때의 예외 (중복 등록, 잘못된 상태 전이 등).
 * <p>R2-3 — {@code @ResponseStatus(409)} 다형 매핑. handleDomain 이 hierarchy 로 흡수하므로 하위
 * (FieldBoundConflict 등)도 일관 409. plain-body 전용 핸들러 수렴.</p>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public abstract class ConflictException extends DomainException {

	protected ConflictException(String message) {
		super(message);
	}
}
