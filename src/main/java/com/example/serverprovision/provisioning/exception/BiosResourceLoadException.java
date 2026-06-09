package com.example.serverprovision.provisioning.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * BIOS 레지스트리(JSON) / SetupData(XML) 리소스의 IO·파싱 실패 (500).
 * 잘못된 루트 요소, 중복 PageID, 지원하지 않는 속성 타입 등 데이터 무결성 위반 포함.
 */
public class BiosResourceLoadException extends DomainException {

	public BiosResourceLoadException(String message) {
		super(message);
	}

	public BiosResourceLoadException(String message, Throwable cause) {
		super(message, cause);
	}
}
