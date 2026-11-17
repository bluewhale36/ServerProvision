package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 대상 트리 루트 경로가 이미 다른 활성 Subprogram 자원에 점유되었을 때.
 * <p>S4 — targetDirectory 필드 직결.</p>
 */
public class SubprogramPathConflictException extends FieldBoundConflictException {

	public SubprogramPathConflictException(String treeRootPath) {
		super("대상 디렉토리를 이미 다른 자원이 쓰고 있습니다: " + treeRootPath, "targetDirectory");
	}
}
