package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 요청한 Subprogram 레코드가 없을 때.
 */
public class SubprogramNotFoundException extends NotFoundException {

	public SubprogramNotFoundException(Long subprogramId) {
		super("Subprogram 을 찾을 수 없습니다. subprogramId=" + subprogramId);
	}
}
