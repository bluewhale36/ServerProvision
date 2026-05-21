package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.vo.BoardScope;

/**
 * 같은 (kind, boardScope, name, version) 활성 자원이 이미 존재할 때 던진다.
 * <p>S4 — version 필드 직결.</p>
 */
public class DuplicateSubprogramVersionException extends FieldBoundConflictException {

	public DuplicateSubprogramVersionException(SubprogramKind kind, BoardScope scope, String name, String version) {
		super(
				"같은 " + kind.getDisplayName() + " 가 이미 등록되어 있습니다. scope=" + scope.pathToken()
						+ ", name=" + name + ", version=" + version, "version"
		);
	}
}
