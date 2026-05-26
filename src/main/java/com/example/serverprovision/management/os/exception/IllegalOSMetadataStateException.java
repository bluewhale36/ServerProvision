package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 요청 시점의 엔티티 상태가 해당 조작을 허용하지 않을 때 던진다.
 * 예: 삭제된 OS 메타데이터에 토글/수정을 시도, 활성 상태에 복구를 시도 등.
 */
public class IllegalOSMetadataStateException extends ConflictException {

	public IllegalOSMetadataStateException(String message) {
		super(message);
	}
}
