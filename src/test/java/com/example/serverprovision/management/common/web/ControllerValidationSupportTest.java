package com.example.serverprovision.management.common.web;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ControllerValidationSupport} 정적 헬퍼 단위 테스트.
 *
 * <p>MA7 — 원래 {@code BoardControllerSupportTest} 에 있던 {@code toValidationError} 검증을
 * 메서드의 공통 승격과 함께 이 파일로 옮겼다(테스트는 코드 위치를 따라간다).</p>
 */
class ControllerValidationSupportTest {

	@Test
	@DisplayName("toValidationError — 필드 에러 2건 → fieldErrors 2건 + 요약 메시지")
	void toValidationError_mapsFieldErrors() {
		BindingResult br = newBindingResult();
		br.addError(fieldError("modelName", "모델명을 입력하세요."));
		br.addError(fieldError("vendor", "제조사를 선택하세요."));

		ApiErrorResponse response = ControllerValidationSupport.toValidationError(br);

		assertThat(response.fieldErrors()).hasSize(2);
		assertThat(response.message()).contains("2개 필드");
	}

	@Test
	@DisplayName("toValidationError — 단일 필드 에러 → field/message 정확히 매핑")
	void toValidationError_singleFieldError() {
		BindingResult br = newBindingResult();
		br.addError(fieldError("modelName", "모델명을 입력하세요."));

		ApiErrorResponse response = ControllerValidationSupport.toValidationError(br);

		assertThat(response.fieldErrors()).hasSize(1);
		assertThat(response.fieldErrors().get(0).field()).isEqualTo("modelName");
		assertThat(response.fieldErrors().get(0).message()).isEqualTo("모델명을 입력하세요.");
		assertThat(response.message()).contains("1개 필드");
	}

	@Test
	@DisplayName("toValidationError — 에러 없는 BindingResult → fieldErrors 빈 목록 + 0개 요약")
	void toValidationError_noErrors() {
		BindingResult br = newBindingResult();

		ApiErrorResponse response = ControllerValidationSupport.toValidationError(br);

		assertThat(response.fieldErrors()).isEmpty();
		assertThat(response.message()).contains("0개 필드");
	}

	@Test
	@DisplayName("toValidationError — defaultMessage null → fallback 메시지로 대체")
	void toValidationError_nullDefaultMessage() {
		BindingResult br = newBindingResult();
		// defaultMessage 가 null 인 FieldError → 헬퍼가 "유효하지 않은 값" 으로 대체.
		br.addError(fieldError("modelName", null));

		ApiErrorResponse response = ControllerValidationSupport.toValidationError(br);

		assertThat(response.fieldErrors()).hasSize(1);
		assertThat(response.fieldErrors().get(0).message()).isEqualTo("유효하지 않은 값");
	}

	private static BindingResult newBindingResult() {
		return new BeanPropertyBindingResult(new Object(), "raidCardForm");
	}

	/**
	 * bean property 해석을 우회해 {@link FieldError} 를 직접 주입한다.
	 * {@code defaultMessage} 에 null 을 넘기면 헬퍼의 fallback 분기를 검증할 수 있다.
	 */
	private static FieldError fieldError(String field, String defaultMessage) {
		return new FieldError(
				"raidCardForm", field, null, false, null, null, defaultMessage);
	}
}
