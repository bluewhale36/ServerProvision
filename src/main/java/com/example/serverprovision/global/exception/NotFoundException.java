package com.example.serverprovision.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 지정한 ID/키에 해당하는 리소스를 찾지 못했을 때의 예외.
 * <p>R2-3 — {@code @ResponseStatus(404)} 다형 매핑. 양 advice 의 {@code handleDomain} 이
 * {@code AnnotationUtils.findAnnotation} 으로 hierarchy 를 타고 흡수한다(plain-body 전용 핸들러 수렴).</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public abstract class NotFoundException extends DomainException {

	protected NotFoundException(String message) {
		super(message);
	}
}
