package com.example.serverprovision.maintenance.trash.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.marker.ResourceType;

/**
 * HF4-1 — 보존기간 연장을 지원하지 않는 자원 종류(메타 자원 등)에 대한 연장 요청 거절.
 *
 * <p>정상 UX 에서는 UI 가 연장 버튼을 disabled + tooltip 으로 선차단하므로, 본 예외는 direct POST 같은
 * 비정상 경로에서만 발동하는 서버 가드 안전망이다. {@link ConflictException} 하위라 기존 advice
 * hierarchy(WebExceptionHandler / ApiExceptionHandler 의 handleDomain)가 409 로 자동 매핑 —
 * advice 수정 불요.</p>
 */
public class TtlExtensionUnsupportedException extends ConflictException {

	public TtlExtensionUnsupportedException(ResourceType resourceType) {
		super(resourceType.getDisplayName() + " 자원은 보존기간 연장을 지원하지 않습니다.");
	}
}
