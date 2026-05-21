package com.example.serverprovision.global.exception;

/**
 * 필드 직결 충돌 예외 (409). S4 의 Layer C 도메인 invariant 위반 중 특정 입력 필드와 1:1 로 매핑되는 경우 사용.
 * <p>예 : 동일 (board, version) BIOS 재등록 → version 필드와 직결. 클라이언트는 응답의 fieldErrors[0].field
 * 로 폼 input 의 {@code data-error-field} 매핑을 자동 처리.</p>
 * <p>일반 충돌 (필드 직결 아님) 은 {@link ConflictException} 을 그대로 사용한다.</p>
 */
public abstract class FieldBoundConflictException extends ConflictException {

	private final String fieldName;

	protected FieldBoundConflictException(String message, String fieldName) {
		super(message);
		this.fieldName = fieldName;
	}

	public String fieldName() {
		return fieldName;
	}
}
