package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3-2 — {@link BoardControllerSupport} 정적 헬퍼 단위 테스트.
 *
 * <p>fat {@code BoardModelController} 3분할 과정에서 추출된 공통 view/응답 헬퍼의 happy + edge 검증.
 * final class + private 생성자라 static 메서드를 직접 호출한다.</p>
 */
class BoardControllerSupportTest {

    // ==== redirectToListWithSelect ===================================

    @Test
    @DisplayName("redirectToListWithSelect — selectId 를 query 로 부착한 redirect URL")
    void redirectToListWithSelect_appendsSelectId() {
        assertThat(BoardControllerSupport.redirectToListWithSelect(7L))
                .isEqualTo("redirect:/management/board?selectId=7");
    }

    @Test
    @DisplayName("redirectToListWithSelect — null selectId 도 그대로 직렬화 (NPE 없음)")
    void redirectToListWithSelect_nullSelectId() {
        assertThat(BoardControllerSupport.redirectToListWithSelect(null))
                .isEqualTo("redirect:/management/board?selectId=null");
    }

    // ==== nullToEmpty ================================================

    @Test
    @DisplayName("nullToEmpty — null → 빈 문자열")
    void nullToEmpty_nullBecomesEmpty() {
        assertThat(BoardControllerSupport.nullToEmpty(null)).isEmpty();
    }

    @Test
    @DisplayName("nullToEmpty — 비-null 값은 그대로 보존")
    void nullToEmpty_nonNullPreserved() {
        assertThat(BoardControllerSupport.nullToEmpty("desc")).isEqualTo("desc");
    }

    @Test
    @DisplayName("nullToEmpty — 빈 문자열은 그대로 빈 문자열")
    void nullToEmpty_emptyStaysEmpty() {
        assertThat(BoardControllerSupport.nullToEmpty("")).isEmpty();
    }

    // ==== toValidationError ==========================================

    @Test
    @DisplayName("toValidationError — 필드 에러 2건 → fieldErrors 2건 + 요약 메시지")
    void toValidationError_mapsFieldErrors() {
        BindingResult br = newBindingResult();
        br.addError(fieldError("modelName", "모델명을 입력하세요."));
        br.addError(fieldError("vendor", "제조사를 선택하세요."));

        ApiErrorResponse response = BoardControllerSupport.toValidationError(br);

        assertThat(response.fieldErrors()).hasSize(2);
        assertThat(response.message()).contains("2개 필드");
    }

    @Test
    @DisplayName("toValidationError — 단일 필드 에러 → field/message 정확히 매핑")
    void toValidationError_singleFieldError() {
        BindingResult br = newBindingResult();
        br.addError(fieldError("modelName", "모델명을 입력하세요."));

        ApiErrorResponse response = BoardControllerSupport.toValidationError(br);

        assertThat(response.fieldErrors()).hasSize(1);
        assertThat(response.fieldErrors().get(0).field()).isEqualTo("modelName");
        assertThat(response.fieldErrors().get(0).message()).isEqualTo("모델명을 입력하세요.");
        assertThat(response.message()).contains("1개 필드");
    }

    @Test
    @DisplayName("toValidationError — 에러 없는 BindingResult → fieldErrors 빈 목록 + 0개 요약")
    void toValidationError_noErrors() {
        BindingResult br = newBindingResult();

        ApiErrorResponse response = BoardControllerSupport.toValidationError(br);

        assertThat(response.fieldErrors()).isEmpty();
        assertThat(response.message()).contains("0개 필드");
    }

    @Test
    @DisplayName("toValidationError — defaultMessage null → fallback 메시지로 대체")
    void toValidationError_nullDefaultMessage() {
        BindingResult br = newBindingResult();
        // defaultMessage 가 null 인 FieldError → 헬퍼가 "유효하지 않은 값" 으로 대체.
        br.addError(fieldError("modelName", null));

        ApiErrorResponse response = BoardControllerSupport.toValidationError(br);

        assertThat(response.fieldErrors()).hasSize(1);
        assertThat(response.fieldErrors().get(0).message()).isEqualTo("유효하지 않은 값");
    }

    private static BindingResult newBindingResult() {
        return new BeanPropertyBindingResult(new Object(), "boardModelForm");
    }

    /**
     * bean property 해석을 우회해 {@link FieldError} 를 직접 주입한다 (FormStub getter 의존 회피).
     * {@code defaultMessage} 에 null 을 넘기면 헬퍼의 fallback 분기를 검증할 수 있다.
     */
    private static FieldError fieldError(String field, String defaultMessage) {
        return new FieldError(
                "boardModelForm", field, null, false, null, null, defaultMessage);
    }
}
