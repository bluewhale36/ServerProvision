package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.DomainException;

public class InvalidBrowsePathException extends DomainException {

	public InvalidBrowsePathException(String path) {
		super("경로 형식이 올바르지 않습니다 : " + path);
	}
}
