package com.example.serverprovision.global.exception;

/**
 * <b>프레젠테이션 계층</b> 예외 — 특정 요청 DTO 필드에 귀속되는 검증 실패를 표현한다.
 *
 * <p>이 예외는 resolver/service 레이어에서만 직접 throw 한다. 도메인 모델은 이 예외를
 * 알지 않으며, 대신 {@link DomainValidationException} (Reason enum 태그) 을 던진다.
 * resolver 가 도메인 예외를 catch 해서 이 예외로 rewrap 할 때 "도메인 Reason → DTO
 * 필드" 매핑이 단일 포인트에서 수행된다. 이 규약 덕분에:
 *
 * <ul>
 *     <li>도메인 모델이 DTO 필드명 리네임의 영향을 받지 않는다 (DDD 계층 경계 유지).</li>
 *     <li>DTO 리네임 시 수정 포인트가 resolver 의 매핑 switch 한 곳으로 국한된다.</li>
 * </ul>
 *
 * <p>{@code field} 값은 요청 DTO 기준의 로컬 필드 이름(예: {@code "partitions"},
 * {@code "boardModelId"}) 을 사용한다. {@code SettingService.resolveOneAtIndex()} 가
 * {@code processList[i].} 인덱스 프리픽스를 덧붙여 Bean Validation 이 생성하는 경로와
 * 동일한 형식({@code processList[0].partitions}) 으로 가공한다.
 *
 * <p>{@link GlobalExceptionHandler} 는 이 예외를 {@link ErrorResponse}.fieldErrors
 * 에 담아 클라이언트로 전달하고, 프론트의 {@code form-error.js} 가 경로 → DOM 매칭
 * ({@code data-error-field}) 으로 인라인 에러를 표시한다.
 *
 * @see DomainValidationException  도메인 계층에서 던지는 대응 예외 (Reason enum 기반)
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public FieldValidationException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
