package com.example.serverprovision.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HF5 — 상태 코드 문구 조회 규약.
 *
 * <p>이 표가 오류 페이지 문구의 단일 소스다. advice 경로({@link WebExceptionHandler})와 Spring Boot 기본
 * {@code /error} 경로({@link ProvisionErrorAttributes})가 같은 값을 보게 하는 것이 목적이므로,
 * 등록된 상태와 등록되지 않은 상태의 동작을 함께 못박는다.</p>
 */
@DisplayName("HF5 — ErrorStatusLabels 상태 문구 조회")
class ErrorStatusLabelsTest {

	@Test
	@DisplayName("등록된 상태는 한국어 문구를 돌려준다")
	void registeredStatus_returnsKoreanLabel() {
		assertThat(ErrorStatusLabels.of(404)).isEqualTo("페이지를 찾을 수 없습니다");
		assertThat(ErrorStatusLabels.of(409)).isEqualTo("다른 작업과 충돌했습니다");
		assertThat(ErrorStatusLabels.of(400)).isEqualTo("요청 내용이 올바르지 않습니다");
		assertThat(ErrorStatusLabels.of(500)).isEqualTo("서버에서 오류가 발생했습니다");
	}

	@Test
	@DisplayName("표에 없는 상태는 기본 문구로 떨어진다 — 상태가 늘어도 화면이 깨지지 않는다")
	void unregisteredStatus_fallsBackToDefault() {
		assertThat(ErrorStatusLabels.of(418)).isEqualTo("요청을 처리하지 못했습니다");
		assertThat(ErrorStatusLabels.of(451)).isEqualTo("요청을 처리하지 못했습니다");
	}

	@Test
	@DisplayName("HTTP 상태가 아닌 값도 기본 문구로 수렴한다 — 조회가 예외를 던지지 않는다")
	void invalidStatusCode_doesNotThrow() {
		assertThat(ErrorStatusLabels.of(0)).isEqualTo("요청을 처리하지 못했습니다");
		assertThat(ErrorStatusLabels.of(-1)).isEqualTo("요청을 처리하지 못했습니다");
		assertThat(ErrorStatusLabels.of(999)).isEqualTo("요청을 처리하지 못했습니다");
	}

	@Test
	@DisplayName("문구에 상태 코드나 영어 reason phrase 를 섞지 않는다 — 코드는 화면이 괄호로 따로 붙인다")
	void labelCarriesNoCodeOrEnglish() {
		for (int status : new int[]{400, 401, 403, 404, 405, 408, 409, 413, 415, 422, 500, 502, 503, 504}) {
			String label = ErrorStatusLabels.of(status);
			assertThat(label)
					.as("status %d 문구", status)
					.doesNotContain(String.valueOf(status))
					.doesNotContainPattern("[A-Za-z]");
		}
	}
}
