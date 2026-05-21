package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.ConflictException;

public class BrowseTargetNotDirectoryException extends ConflictException {

	public BrowseTargetNotDirectoryException(String path) {
		super("디렉토리가 아닙니다 : " + path);
	}
}
