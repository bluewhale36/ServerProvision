package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.List;

/**
 * 번들 트리 루트에 복수 {@code .nsh} 가 있고 자동 탐지 규칙으로 단일 파일을 고를 수 없는 상태.
 * 관리자가 {@code entrypointRelativePath} 로 하나를 명시적으로 지정해야 한다.
 */
public class EntrypointAmbiguousException extends ConflictException {

	public EntrypointAmbiguousException(List<String> candidates) {
		super("진입점 후보가 여러 개입니다. 하나를 명시 지정하세요 : " + String.join(", ", candidates));
	}
}
