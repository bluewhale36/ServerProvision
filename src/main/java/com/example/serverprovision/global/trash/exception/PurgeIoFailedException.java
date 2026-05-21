package com.example.serverprovision.global.trash.exception;

import com.example.serverprovision.global.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * S5-2-4 — hard-delete 도중 파일시스템 / DB I/O 실패. PurgeExecutor 내부에서
 * scanner.purgeFromTrash 가 throw — TTL_AUTO origin 의 retry 대상.
 *
 * <p>SERVICE_UNAVAILABLE(503) 매핑 — 일시 장애 (read-only fs 등). 사용자 진입에서
 * 직접 노출 가능하나, TTL_AUTO 진입은 retry 후 markFailed 로 흡수되어 사용자에게 안 보임.</p>
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PurgeIoFailedException extends DomainException {

	public PurgeIoFailedException(String message) {
		super(message);
	}

	public PurgeIoFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
