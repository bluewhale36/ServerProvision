package com.example.serverprovision.provisioning.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 요청한 boardKey 가 {@code provisioning.bios.boards} 설정에 없을 때 (404).
 */
public class BiosBoardNotFoundException extends NotFoundException {

	public BiosBoardNotFoundException(String boardKey) {
		super("BIOS 보드를 찾을 수 없습니다. board=" + boardKey);
	}
}
