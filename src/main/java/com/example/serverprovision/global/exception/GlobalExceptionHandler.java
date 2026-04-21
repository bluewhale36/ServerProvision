package com.example.serverprovision.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * {@code application.setting} 패키지 내 JSON API 엔드포인트용 전역 예외 핸들러.
 *
 * <p>적용 범위를 {@code com.example.serverprovision.application.setting} 패키지로
 * 제한해, 관리자 MVC 폼 컨트롤러(@ModelAttribute + BindingResult, {@code application.admin}
 * 패키지) 의 HTML 응답 흐름을 JSON 으로 덮어쓰지 않는다. setting 패키지 하위에 신규
 * JSON 엔드포인트가 추가되면 자동으로 이 핸들러의 적용 범위에 포함된다.
 *
 * <p>응답 스키마는 {@link ErrorResponse} 참고. 프론트의 {@code form-error.js} 가
 * {@code fieldErrors[].field} 경로를 파싱해 {@code data-error-field} DOM 마커와
 * 매칭하므로 각 핸들러는 사용자-친화적 {@code message} 와 경로 형식이 일관된
 * {@code fieldErrors} 를 채워야 한다.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.example.serverprovision.application.setting")
public class GlobalExceptionHandler {

    /**
     * {@code @Valid} 요청 바디 검증 실패 (e.g. name 빈 값, processList 빈 배열).
     * 첫 필드 에러를 대표 메시지로 노출하고, 전체 목록은 {@code fieldErrors} 에 담는다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage()))
                .toList();
        String summary = fieldErrors.isEmpty()
                ? "요청 값 검증에 실패했습니다."
                : fieldErrors.get(0).message();
        log.warn("[GlobalExceptionHandler] Validation 실패. fieldErrors={}", fieldErrors);
        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(summary, fieldErrors));
    }

    /**
     * 도메인/서비스 계층에서 특정 필드에 귀속되는 검증 실패.
     * Bean Validation 과 동일한 {@link ErrorResponse.FieldError} 포맷으로 포장해
     * 프론트가 단일 경로 → DOM 매칭 로직 하나로 두 경우를 모두 처리할 수 있게 한다.
     */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException ex) {
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError(ex.getField(), ex.getMessage());
        log.warn("[GlobalExceptionHandler] FieldValidationException. field={}, message={}",
                ex.getField(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ErrorResponse.ofValidation(ex.getMessage(), List.of(fieldError)));
    }

    /**
     * 잘못된 JSON 본문 또는 Jackson 다형성 {@code type} / {@code osFamily} 판별자 미스매치.
     *
     * <p>특히 다형성 역직렬화 실패({@link tools.jackson.databind.exc.InvalidTypeIdException})
     * 는 사용자가 해당 단계의 필수 선택(OS 종류 등)을 놓쳐서 발생하는 경우가 대부분이므로,
     * 원인 예외 체인을 검사해 문제가 된 스텝·필드를 {@link ErrorResponse.FieldError} 로 변환한다.
     * 브라우저가 구 JS 를 캐시해 클라이언트 가드가 우회된 경우에도 프론트가 인라인 에러를
     * 표시할 수 있도록 하는 심층 방어선이다.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("[GlobalExceptionHandler] JSON 파싱 실패. message={}", ex.getMessage());

        ErrorResponse.FieldError extracted = extractPolymorphicFieldError(ex);
        if (extracted != null) {
            return ResponseEntity.badRequest().body(
                    ErrorResponse.ofValidation(extracted.message(), List.of(extracted)));
        }

        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "MALFORMED_JSON",
                "요청 본문의 JSON 형식이 올바르지 않거나 알 수 없는 프로세스 타입입니다."));
    }

    /**
     * Jackson 3 ({@code tools.jackson.*}) 의 {@link tools.jackson.databind.exc.InvalidTypeIdException}
     * 또는 동일 시맨틱의 DatabindException 을 감지해 어떤 스텝의 어떤 판별자가 누락/미스매치인지
     * 추정하고 사용자용 필드 에러로 변환한다. 확실히 식별 불가하면 {@code null} 반환 후 상위에서
     * 일반 MALFORMED_JSON 메시지로 폴백한다.
     */
    private ErrorResponse.FieldError extractPolymorphicFieldError(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof tools.jackson.databind.exc.InvalidTypeIdException itid) {
                // 경로에서 processList 인덱스를 복원 (없으면 일반 안내).
                String path = joinPath(itid);
                String baseTypeName = itid.getBaseType() != null
                        ? itid.getBaseType().getRawClass().getSimpleName() : "";
                String typeId = itid.getTypeId();

                String fieldPath = path.isEmpty() ? "processList" : path;
                // 베이스 타입이 OSSettingRequest / OSInstallationRequest 이면 osFamily 판별자 미스매치.
                if ("OSSettingRequest".equals(baseTypeName)) {
                    return new ErrorResponse.FieldError(
                            fieldPath.endsWith(".osFamily") ? fieldPath : fieldPath + ".osFamily",
                            "OS 설정 단계의 OS 계열을 결정할 수 없습니다. 대상 OS 종류와 버전을 선택했는지 확인해 주세요."
                                    + (isBlank(typeId) ? "" : " (수신값: '" + typeId + "')"));
                }
                if ("OSInstallationRequest".equals(baseTypeName)) {
                    return new ErrorResponse.FieldError(
                            fieldPath.endsWith(".osFamily") ? fieldPath : fieldPath + ".osFamily",
                            "OS 설치 단계의 OS 계열을 결정할 수 없습니다. 대상 OS 종류와 버전을 선택했는지 확인해 주세요."
                                    + (isBlank(typeId) ? "" : " (수신값: '" + typeId + "')"));
                }
                // AbstractProcessRequest 수준의 type 판별자 미스매치.
                if ("AbstractProcessRequest".equals(baseTypeName)) {
                    return new ErrorResponse.FieldError(
                            fieldPath.endsWith(".type") ? fieldPath : fieldPath + ".type",
                            "지원하지 않는 프로세스 타입입니다."
                                    + (isBlank(typeId) ? "" : " (수신값: '" + typeId + "')"));
                }
            }
            cause = cause.getCause();
        }

        // 폴백: 예외 메시지 텍스트로 베이스 타입 추정.
        return extractPolymorphicFieldErrorFromMessage(ex.getMessage() == null ? "" : ex.getMessage());
    }

    /**
     * Jackson 예외 메시지에서 베이스 타입 이름을 찾아 사용자용 필드 에러로 변환한다.
     * Jackson 3 가 InvalidTypeIdException 외 다른 파생 예외(MismatchedInputException 등) 로 던져도
     * 동일한 "Cannot construct instance of X" 패턴을 유지하므로 메시지 기반 매칭이 안정적이다.
     */
    private ErrorResponse.FieldError extractPolymorphicFieldErrorFromMessage(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.contains("OSSettingRequest")) {
            return new ErrorResponse.FieldError(
                    "processList",
                    "OS 설정 단계의 OS 계열을 결정할 수 없습니다. 대상 OS 종류와 버전을 선택했는지 확인해 주세요.");
        }
        if (raw.contains("OSInstallationRequest")) {
            return new ErrorResponse.FieldError(
                    "processList",
                    "OS 설치 단계의 OS 계열을 결정할 수 없습니다. 대상 OS 종류와 버전을 선택했는지 확인해 주세요.");
        }
        if (raw.contains("AbstractProcessRequest")) {
            return new ErrorResponse.FieldError(
                    "processList",
                    "지원하지 않는 프로세스 타입입니다.");
        }
        return null;
    }

    private String joinPath(tools.jackson.databind.DatabindException ex) {
        try {
            var ref = ex.getPath();
            if (ref == null || ref.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (var p : ref) {
                // Jackson 3: getPropertyName() (구 getFieldName())
                String prop = p.getPropertyName();
                if (prop != null) {
                    if (sb.length() > 0) sb.append('.');
                    sb.append(prop);
                } else if (p.getIndex() >= 0) {
                    sb.append('[').append(p.getIndex()).append(']');
                }
            }
            return sb.toString();
        } catch (Exception ignore) {
            return "";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 도메인/서비스 계층에서 던지는 인자 오류. resolver 의 엔티티 조회 실패,
     * 패키지 그룹 환경 불일치, 마운트포인트 누락, OSTemplate 호환성 실패 등이 여기로 떨어진다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[GlobalExceptionHandler] IllegalArgumentException. message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_ARGUMENT", ex.getMessage()));
    }

    /**
     * 미지원 OS 타입 선택 등 런타임 단계에서 지원되지 않는 연산.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupported(UnsupportedOperationException ex) {
        log.warn("[GlobalExceptionHandler] UnsupportedOperationException. message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of("UNSUPPORTED_OPERATION", ex.getMessage()));
    }

    /**
     * PENDING이 아닌 상태의 주문서 수정 시도 등 허용되지 않는 상태 전이.
     * 409 Conflict로 응답하여 낙관적 잠금 실패와 동일한 시맨틱을 부여한다.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("[GlobalExceptionHandler] IllegalStateException. message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse.of("INVALID_STATE", ex.getMessage()));
    }

    /**
     * 예측하지 못한 오류. 원문은 로그에만 남기고 클라이언트에게는 일반화된 메시지를 반환해
     * 내부 정보 노출을 최소화한다.
     */
    /**
     * Spring 이 HttpMessageNotReadableException 으로 감싸지 않고 직접 올라온 Jackson
     * {@link tools.jackson.databind.DatabindException} 도 동일 경로로 처리한다.
     * 일부 Jackson 3 조합에서 발생한다.
     */
    @ExceptionHandler(tools.jackson.databind.DatabindException.class)
    public ResponseEntity<ErrorResponse> handleDatabindDirect(tools.jackson.databind.DatabindException ex) {
        log.warn("[GlobalExceptionHandler] DatabindException 직접 발생. message={}", ex.getMessage());
        ErrorResponse.FieldError extracted = extractPolymorphicFieldErrorFromMessage(
                ex.getMessage() == null ? "" : ex.getMessage());
        if (extracted != null) {
            return ResponseEntity.badRequest().body(
                    ErrorResponse.ofValidation(extracted.message(), List.of(extracted)));
        }
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "MALFORMED_JSON",
                "요청 본문의 JSON 형식이 올바르지 않거나 알 수 없는 프로세스 타입입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // 최종 폴백도 Jackson 다형성 메시지 패턴이면 사용자용 필드 에러로 승격한다.
        // (일부 Jackson 3 경로에서 DatabindException 이 HttpMessageNotReadableException 으로
        //  감싸이지 않고 원인 체인 깊은 곳에만 묻히는 케이스가 관찰됨)
        ErrorResponse.FieldError extracted =
                extractPolymorphicFieldErrorFromMessage(ex.getMessage() == null ? "" : ex.getMessage());
        if (extracted == null) {
            // 원인 체인도 탐색
            for (Throwable c = ex.getCause(); c != null; c = c.getCause()) {
                extracted = extractPolymorphicFieldErrorFromMessage(
                        c.getMessage() == null ? "" : c.getMessage());
                if (extracted != null) break;
            }
        }
        if (extracted != null) {
            log.warn("[GlobalExceptionHandler] Jackson 다형성 미스매치 (폴백 경로). message={}",
                    ex.getMessage());
            return ResponseEntity.badRequest().body(
                    ErrorResponse.ofValidation(extracted.message(), List.of(extracted)));
        }

        log.error("[GlobalExceptionHandler] 예상치 못한 오류.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
