package com.example.serverprovision.management.common.filesystem.exception;

import com.example.serverprovision.global.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * R2-3 — browse 4-catch advice 승급에 따라 {@code @ResponseStatus(400)} 부착. 나머지 3 browse 예외는 이미
 * {@code BrowseTargetNotFoundException extends NotFoundException}(404) /
 * {@code BrowseTargetNotDirectoryException extends ConflictException}(409) /
 * {@code DirectoryBrowseIoException extends DomainException}(handleDomain 500) 으로 계층 정합이라 어노테이션 불요.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBrowsePathException extends DomainException {

	public InvalidBrowsePathException(String path) {
		super("경로 형식이 올바르지 않습니다 : " + path);
	}
}
