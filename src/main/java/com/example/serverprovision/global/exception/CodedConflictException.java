package com.example.serverprovision.global.exception;

/**
 * 화면이 메시지 문자열이 아니라 안정된 코드로 갈라 처리해야 하는 409(HF30-3).
 *
 * <p>advice 는 이 타입 하나로 받아 {@code code} 를 응답에 싣는다. 코드가 필요한 409 가 늘어도 핸들러는 늘지 않는다.
 * 선례: 중복 확인 만료(R2-5), 영구 삭제 뒤 등록 실패(HF30-3).</p>
 */
public abstract class CodedConflictException extends ConflictException {

	protected CodedConflictException(String message) {
		super(message);
	}

	/** 화면이 분기에 쓰는 안정된 코드. 메시지 문구가 바뀌어도 그대로 둔다. */
	public abstract String code();
}
