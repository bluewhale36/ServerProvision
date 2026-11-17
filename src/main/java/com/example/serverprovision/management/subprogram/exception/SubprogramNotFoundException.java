package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 요청한 Subprogram 레코드가 없을 때.
 */
public class SubprogramNotFoundException extends NotFoundException {

	public SubprogramNotFoundException(Long subprogramId) {
		super("드라이버 또는 유틸리티를 찾을 수 없습니다.");
	}
}
