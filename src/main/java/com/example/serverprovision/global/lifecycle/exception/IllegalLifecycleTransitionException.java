package com.example.serverprovision.global.lifecycle.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK2 — softDelete / restore / purge 전이 위반.
 *
 * <ul>
 *   <li>Active 자원에 restore() 호출 (이미 active)</li>
 *   <li>SoftDeleted 가 아닌 자원에 purge() 호출</li>
 *   <li>이미 SoftDeleted 자원에 softDelete() 호출</li>
 * </ul>
 */
public class IllegalLifecycleTransitionException extends ConflictException {

	public IllegalLifecycleTransitionException(String message) {
		super(message);
	}
}
