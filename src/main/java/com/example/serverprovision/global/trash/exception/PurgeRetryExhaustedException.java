package com.example.serverprovision.global.trash.exception;

import com.example.serverprovision.global.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * S5-2-4 — TTL_AUTO 의 누적 FAILED 가 임계 (default 3) 를 초과한 시점에 격상.
 *
 * <p>PurgeExecutor 자체가 throw 하지는 않음 — 운영자 알림 (TRASH_PURGE_FAILED Job) 등록 시
 * 본 예외를 cause 로 attached 가능. 또는 retry-failed endpoint 에서 3회 초과 자원만 묶어 운영자에게 보고.</p>
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PurgeRetryExhaustedException extends DomainException {

	public PurgeRetryExhaustedException(String message) {
		super(message);
	}

	public PurgeRetryExhaustedException(String message, Throwable cause) {
		super(message, cause);
	}
}
