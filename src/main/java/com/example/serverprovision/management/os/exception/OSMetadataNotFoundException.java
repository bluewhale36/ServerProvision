package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 지정 ID 의 OS 메타데이터가 존재하지 않을 때 던진다.
 * soft 삭제된 레코드에 대해 삭제/토글/수정을 시도하는 경우에도 NotFound 로 취급한다.
 */
public class OSMetadataNotFoundException extends NotFoundException {

	public OSMetadataNotFoundException(Long id) {
		super("OS 버전을 찾을 수 없습니다. id=" + id);
	}
}
