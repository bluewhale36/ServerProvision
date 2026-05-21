package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * Subprogram 의 현재 상태와 요청이 맞지 않을 때 (이미 활성/이미 삭제 등).
 */
public class IllegalSubprogramStateException extends ConflictException {

	public IllegalSubprogramStateException(String message) {
		super(message);
	}
}
