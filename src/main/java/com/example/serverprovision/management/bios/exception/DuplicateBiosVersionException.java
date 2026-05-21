package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 같은 BoardModel 범위에서 활성 BIOS 의 version 이 중복될 때 던진다. HTTP 409.
 * <p>S4 — version 필드 직결.</p>
 */
public class DuplicateBiosVersionException extends FieldBoundConflictException {

	public DuplicateBiosVersionException(Long boardId, String version) {
		super("같은 메인보드에 이미 같은 버전의 BIOS 가 등록되어 있습니다. boardId=" + boardId + ", version=" + version, "version");
	}
}
