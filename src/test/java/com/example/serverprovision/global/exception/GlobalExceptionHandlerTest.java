package com.example.serverprovision.global.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.type.TypeFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GlobalExceptionHandler} 다형성 역직렬화 방어선 테스트.
 *
 * <p>리플렉션 기반 테스트가 아니라 실제 Jackson 3 예외 타입 (InvalidTypeIdException, DatabindException)
 * 을 직접 조립해 핸들러가 베이스 타입별로 올바른 메시지·필드 에러를 만들어내는지 검증한다.</p>
 *
 * <p><b>주의 — 합성 예외 한계</b>: 이 테스트는 {@code InvalidTypeIdException} 를 직접 조립해
 * 핸들러에 넘기는 방식이므로, Jackson 3 가 실제 {@code ObjectMapper} 역직렬화 중 어떤 구체
 * 예외 타입을 던지고 어떤 Spring wrapper ({@code HttpMessageNotReadableException} 또는
 * {@code HttpMessageConversionException}) 를 거쳐 advice 에 도달하는지는 검증하지 않는다.
 * 실제 Spring MVC 파이프라인을 거친 역직렬화 실패 회귀는
 * {@link com.example.serverprovision.application.setting.controller.SettingValidateRealFlowTest}
 * 에서 {@code @WebMvcTest + MockMvc} 로 커버된다. 실 파이프라인에서 관찰된 예외 타입은
 * (합성과 달리) 주로 {@code ValueInstantiationException} / {@code InvalidDefinitionException}
 * 이며, {@code handleMalformedJson} 의 {@code instanceof InvalidTypeIdException} 분기는 타지
 * 않고 메시지 기반 폴백 경로 ({@code extractPolymorphicFieldErrorFromMessage},
 * {@code handleUnexpected} 의 cause-chain 매칭) 이 실제 동작한다.</p>
 *
 * <p>커버 대상:
 * <ul>
 *   <li>InvalidTypeIdException + baseType=OSSettingRequest → osFamily 필드 에러 + 전용 메시지</li>
 *   <li>InvalidTypeIdException + baseType=OSInstallationRequest → osFamily 필드 에러 + 설치용 메시지</li>
 *   <li>InvalidTypeIdException + baseType=AbstractProcessRequest → type 필드 에러</li>
 *   <li>extractPolymorphicFieldErrorFromMessage — DatabindException 직접 발생 경로</li>
 *   <li>폴백 Exception 핸들러의 cause chain 탐색</li>
 *   <li>FieldValidationException 전달 + 경로·메시지 유지</li>
 *   <li>IllegalArgumentException / IllegalStateException / UnsupportedOperationException 매핑</li>
 * </ul>
 * </p>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // Jackson 3 의 InvalidTypeIdException 생성 헬퍼 — baseType 의 SimpleName 이 핸들러 분기의 근거다.
    private InvalidTypeIdException buildInvalidTypeIdException(Class<?> baseType, String typeId) {
        JavaType javaType = TypeFactory.createDefaultInstance().constructType(baseType);
        return InvalidTypeIdException.from(
                (JsonParser) null,
                "Could not resolve type id '" + typeId + "' as a subtype of `" + baseType.getName() + "`",
                javaType,
                typeId);
    }

    private HttpMessageNotReadableException wrap(Throwable cause) {
        ServletServerHttpRequest req = new ServletServerHttpRequest(
                new org.springframework.mock.web.MockHttpServletRequest());
        return new HttpMessageNotReadableException(cause.getMessage(), cause, req);
    }

    // =========================================================================
    // 다형성 역직렬화 실패 방어
    // =========================================================================

    @Nested
    @DisplayName("HttpMessageNotReadableException + InvalidTypeIdException")
    class PolymorphicFieldErrorTest {

        @Test
        @DisplayName("OSSettingRequest 베이스 — osFamily 필드 에러 + 전용 메시지 + 수신값 표시")
        void osSettingRequest_generatesOsFamilyFieldError() {
            InvalidTypeIdException itid = buildInvalidTypeIdException(
                    com.example.serverprovision.application.setting.model.request.OSSettingRequest.class,
                    "UBUNTU_BASED");
            ResponseEntity<ErrorResponse> response = handler.handleMalformedJson(wrap(itid));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
            assertThat(body.fieldErrors()).hasSize(1);
            assertThat(body.fieldErrors().get(0).field()).endsWith(".osFamily");
            assertThat(body.fieldErrors().get(0).message())
                    .contains("OS 설정 단계")
                    .contains("UBUNTU_BASED"); // 수신값이 메시지에 포함되어야 함
        }

        @Test
        @DisplayName("OSInstallationRequest 베이스 — osFamily 필드 에러 + 설치용 메시지")
        void osInstallationRequest_generatesOsFamilyFieldError() {
            InvalidTypeIdException itid = buildInvalidTypeIdException(
                    com.example.serverprovision.application.setting.model.request.OSInstallationRequest.class,
                    "WINDOWS_BASED");
            ResponseEntity<ErrorResponse> response = handler.handleMalformedJson(wrap(itid));

            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.fieldErrors().get(0).field()).endsWith(".osFamily");
            assertThat(body.fieldErrors().get(0).message()).contains("OS 설치 단계");
        }

        @Test
        @DisplayName("AbstractProcessRequest 베이스 — type 필드 에러")
        void abstractProcessRequest_generatesTypeFieldError() {
            InvalidTypeIdException itid = buildInvalidTypeIdException(
                    com.example.serverprovision.application.setting.model.request.AbstractProcessRequest.class,
                    "UNKNOWN_STEP");
            ResponseEntity<ErrorResponse> response = handler.handleMalformedJson(wrap(itid));

            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.fieldErrors().get(0).field()).endsWith(".type");
            assertThat(body.fieldErrors().get(0).message()).contains("지원하지 않는 프로세스 타입");
        }

        @Test
        @DisplayName("null / 빈 typeId 일 때 '수신값:' 프리픽스가 추가되지 않는다")
        void blankTypeId_doesNotAddReceivedValueSuffix() {
            InvalidTypeIdException itid = buildInvalidTypeIdException(
                    com.example.serverprovision.application.setting.model.request.OSSettingRequest.class, "");

            ResponseEntity<ErrorResponse> response = handler.handleMalformedJson(wrap(itid));

            ErrorResponse body = response.getBody();
            assertThat(body.fieldErrors().get(0).message()).doesNotContain("수신값:");
        }

        @Test
        @DisplayName("판별자 cause chain 탐지 불가 + 메시지에 OSSettingRequest 포함 시 메시지 기반 폴백이 동작한다")
        void fallbackByMessage_whenCauseUnidentifiable() {
            // InvalidTypeIdException 이 아니라 일반 RuntimeException 을 cause 로 두고, 메시지에
            // 베이스 타입 이름을 포함해 메시지 기반 매칭 경로를 타게 한다.
            RuntimeException cause = new RuntimeException(
                    "Cannot construct instance of OSSettingRequest: unknown osFamily");
            ResponseEntity<ErrorResponse> response = handler.handleMalformedJson(wrap(cause));

            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.fieldErrors()).hasSize(1);
            assertThat(body.fieldErrors().get(0).field()).isEqualTo("processList");
            assertThat(body.fieldErrors().get(0).message()).contains("OS 설정 단계");
        }

        @Test
        @DisplayName("메시지에도 베이스 타입이 없으면 MALFORMED_JSON 으로 폴백")
        void fallsBackToMalformedJson_whenNoBaseTypeHint() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleMalformedJson(wrap(new RuntimeException("일반 파싱 오류")));

            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.code()).isEqualTo("MALFORMED_JSON");
            assertThat(body.fieldErrors()).isNull();
        }
    }

    // =========================================================================
    // DatabindException 직접 경로 (Jackson 3 조합 일부)
    // =========================================================================

    @Nested
    @DisplayName("DatabindException 직접 발생 — handleDatabindDirect")
    class DatabindDirectTest {

        @Test
        @DisplayName("메시지에 OSSettingRequest 포함 시 osFamily 필드 에러로 변환")
        void osSettingRequest_inMessage_returnsFieldError() {
            DatabindException ex = buildInvalidTypeIdException(
                    com.example.serverprovision.application.setting.model.request.OSSettingRequest.class,
                    "UNKNOWN");
            ResponseEntity<ErrorResponse> response = handler.handleDatabindDirect(ex);

            ErrorResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
            assertThat(body.fieldErrors()).hasSize(1);
            assertThat(body.fieldErrors().get(0).message()).contains("OS 설정 단계");
        }

        @Test
        @DisplayName("메시지에 OSInstallationRequest 포함 시 설치 메시지로 변환")
        void osInstallationRequest_inMessage_returnsInstallationMessage() {
            DatabindException ex = buildInvalidTypeIdException(
                    com.example.serverprovision.application.setting.model.request.OSInstallationRequest.class,
                    "X");
            ResponseEntity<ErrorResponse> response = handler.handleDatabindDirect(ex);

            ErrorResponse body = response.getBody();
            assertThat(body.fieldErrors().get(0).message()).contains("OS 설치 단계");
        }

        @Test
        @DisplayName("메시지에 베이스 타입 힌트가 없으면 MALFORMED_JSON 으로 폴백")
        void noHint_fallsBackToMalformedJson() {
            // Jackson 3 의 DatabindException 서브타입 생성 — 메시지에 특정 베이스 타입 이름이 없음.
            DatabindException ex = new DatabindException("generic parse failure") {};
            ResponseEntity<ErrorResponse> response = handler.handleDatabindDirect(ex);

            assertThat(response.getBody().code()).isEqualTo("MALFORMED_JSON");
        }
    }

    // =========================================================================
    // 폴백 Exception 핸들러의 cause chain 탐색
    // =========================================================================

    @Nested
    @DisplayName("폴백 Exception — cause chain 기반 메시지 매칭")
    class UnexpectedFallbackTest {

        @Test
        @DisplayName("cause 체인 깊은 곳에 OSInstallationRequest 가 있으면 폴백 필드 에러로 승격된다")
        void causeChainMatch_promotesToFieldError() {
            Throwable deep = new RuntimeException("root: OSInstallationRequest subtype missing");
            Exception mid = new RuntimeException("intermediate", deep);
            Exception wrapped = new RuntimeException("wrapper", mid);

            ResponseEntity<ErrorResponse> response = handler.handleUnexpected(wrapped);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
            assertThat(response.getBody().fieldErrors()).isNotNull();
            assertThat(response.getBody().fieldErrors().get(0).message()).contains("OS 설치 단계");
        }

        @Test
        @DisplayName("cause chain 에도 베이스 타입 힌트가 없으면 500 INTERNAL_ERROR")
        void noHintInChain_returns500() {
            Exception raw = new RuntimeException("알 수 없는 내부 오류");
            ResponseEntity<ErrorResponse> response = handler.handleUnexpected(raw);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        }
    }

    // =========================================================================
    // FieldValidationException
    // =========================================================================

    @Test
    @DisplayName("FieldValidationException 은 지정 필드·메시지 그대로 VALIDATION_FAILED 로 전달된다")
    void handleFieldValidation_preservesFieldAndMessage() {
        FieldValidationException ex =
                new FieldValidationException("processList[0].partitions", "파티션 필수");

        ResponseEntity<ErrorResponse> response = handler.handleFieldValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.message()).isEqualTo("파티션 필수");
        assertThat(body.fieldErrors()).hasSize(1);
        assertThat(body.fieldErrors().get(0).field()).isEqualTo("processList[0].partitions");
    }

    // =========================================================================
    // 표준 예외 매핑
    // =========================================================================

    @Nested
    @DisplayName("표준 예외 매핑")
    class StandardExceptionsTest {

        @Test
        @DisplayName("IllegalArgumentException → 400 + INVALID_ARGUMENT")
        void illegalArgument_returns400() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleIllegalArgument(new IllegalArgumentException("bad arg"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("INVALID_ARGUMENT");
            assertThat(response.getBody().message()).isEqualTo("bad arg");
        }

        @Test
        @DisplayName("UnsupportedOperationException → 400 + UNSUPPORTED_OPERATION")
        void unsupportedOperation_returns400() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleUnsupported(new UnsupportedOperationException("not yet"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_OPERATION");
        }

        @Test
        @DisplayName("IllegalStateException → 409 Conflict + INVALID_STATE")
        void illegalState_returns409Conflict() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleIllegalState(new IllegalStateException("not pending"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("INVALID_STATE");
        }
    }
}
