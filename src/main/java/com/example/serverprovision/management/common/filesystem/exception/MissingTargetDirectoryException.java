package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 기존 디렉토리 등록 모드에서 사용. 지정 경로의 디렉토리가 존재하지 않거나 디렉토리가 아닐 때.
 * <p>업로드 모드의 {@link com.example.serverprovision.management.os.exception.DirectoryMissingException}
 * 은 "상위 디렉토리" 부재 가드라서 의미가 다르다 — 본 예외는 "타겟 자체" 부재.</p>
 */
public class MissingTargetDirectoryException extends FieldBoundConflictException {

	public MissingTargetDirectoryException(String path) {
		super(
				"지정한 디렉토리가 존재하지 않거나 디렉토리가 아닙니다 : " + path,
				"targetDirectory"
		);
	}
}
