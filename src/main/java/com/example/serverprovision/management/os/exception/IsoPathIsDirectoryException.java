package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * HF4-3 (F-4) — 사용자 입력 ISO 경로가 "기존 디렉토리" 를 가리키는 경우 던진다.
 * 파일을 저장하려면 파일명까지 포함한 완전한 경로가 필요하므로 입력성 오류(400)이며 isoPath 필드에 직결된다.
 *
 * <p>정상 화면 JS 는 디렉토리 선택 시 파일명을 이어붙여 완전한 경로를 만들어 보내므로,
 * 본 예외는 direct POST 같은 비정상 경로에서만 발동한다. 가드가 없으면
 * {@code Files.copy} 의 IOException → {@link ISOFileStorageException} → 500 으로 새던 케이스.</p>
 */
public class IsoPathIsDirectoryException extends FieldBoundBadRequestException {

	public IsoPathIsDirectoryException(String resolvedPath) {
		super("경로가 디렉토리입니다. 저장할 파일명까지 포함한 경로를 입력하세요 : " + resolvedPath, "isoPath");
	}
}
