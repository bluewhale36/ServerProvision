package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 요청 경로의 BIOS 레코드가 없거나 부모 BoardModel 에 속하지 않을 때 던진다. HTTP 404.
 */
public class BiosNotFoundException extends NotFoundException {

	public BiosNotFoundException(Long boardId, Long biosId) {
		super("BIOS 를 찾을 수 없습니다. boardId=" + boardId + ", biosId=" + biosId);
	}
}
