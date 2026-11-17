package com.example.serverprovision.management.bmc.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 대상 파일 경로가 이미 다른 자원에 점유되었을 때.
 * <p>S4 — targetDirectory 필드 직결.</p>
 */
public class BmcPathConflictException extends FieldBoundConflictException {

	public BmcPathConflictException(String firmwarePath) {
		super("대상 파일 경로를 이미 다른 자원이 쓰고 있습니다: " + firmwarePath, "targetDirectory");
	}
}
