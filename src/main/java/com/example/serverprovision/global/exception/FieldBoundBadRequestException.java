package com.example.serverprovision.global.exception;

/**
 * 필드 직결 입력 형식 / 비즈니스 검증 실패 예외 (400). S4 의 Layer A (Bean Validation) 로 끌어올리기 어려운
 * cross-field 또는 외부 상태 의존 검증 중 특정 필드와 1:1 매핑되는 경우 사용.
 * <p>예 : ISO 가 선택된 OSImage 의 자식이 아님 → isoId 필드와 직결.</p>
 * <p>field 매핑 없이 단순 400 인 경우는 일반 {@link DomainException} 또는 보안 예외를 사용.</p>
 */
public abstract class FieldBoundBadRequestException extends DomainException {

    private final String fieldName;

    protected FieldBoundBadRequestException(String message, String fieldName) {
        super(message);
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }
}
