package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 업로드 대상 디렉토리에 이미 A3/A4/A5 중 어느 한쪽이 관리하는 {@code .provision.json} 이 존재해
 * 그 자리를 소유하고 있는 상태. 다른 등록을 덮어쓰는 사고 방지를 위해 거절.
 * <p>S4 — targetDirectory 필드 직결.</p>
 */
public class MarkerConflictException extends FieldBoundConflictException {

	public MarkerConflictException(String path) {
		super("이 디렉토리는 이미 다른 등록 소유로 marker 가 있습니다 : " + path, "targetDirectory");
	}
}
