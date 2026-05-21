package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 업로드된 번들에 파일이 0개인 경우. 관리자 실수 (빈 폴더 선택, 빈 zip) 방지용 가드.
 */
public class EmptyBundleException extends ConflictException {

	public EmptyBundleException() {
		super("업로드된 번들이 비어 있습니다. 최소 하나 이상의 파일이 필요합니다.");
	}
}
