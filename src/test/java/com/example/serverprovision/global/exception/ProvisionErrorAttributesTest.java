package com.example.serverprovision.global.exception;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HF29 — 거절한 쪽이 요청 속성(ErrorReason)에 둔 사유를 /error 의 message 로 옮긴다. 없으면 종전 그대로. */
class ProvisionErrorAttributesTest {

	private final ProvisionErrorAttributes attributes = new ProvisionErrorAttributes();

	private static MockHttpServletRequest errorRequest(int status) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
		request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
		request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/users");
		return request;
	}

	@Test
	@DisplayName("사유 속성이 있으면 message = 사유 · statusLabel 은 종전대로")
	void reasonBecomesMessage() {
		MockHttpServletRequest request = errorRequest(403);
		request.setAttribute(ErrorReason.REQUEST_ATTRIBUTE, "사용자 관리는 최고 관리자만 할 수 있습니다.");
		Map<String, Object> result = attributes.getErrorAttributes(new ServletWebRequest(request), ErrorAttributeOptions.defaults());
		assertThat(result).containsEntry("message", "사용자 관리는 최고 관리자만 할 수 있습니다.")
				.containsEntry("statusLabel", "접근 권한이 없습니다")
				.containsEntry("status", 403);
	}

	@Test
	@DisplayName("사유 속성이 없거나 빈 문자열이면 message 를 덮지 않는다(기본 노출 설정상 키 없음)")
	void noReason_unchanged() {
		Map<String, Object> plain = attributes.getErrorAttributes(new ServletWebRequest(errorRequest(404)), ErrorAttributeOptions.defaults());
		assertThat(plain).doesNotContainKey("message").containsEntry("statusLabel", "페이지를 찾을 수 없습니다");

		MockHttpServletRequest blank = errorRequest(403);
		blank.setAttribute(ErrorReason.REQUEST_ATTRIBUTE, "  ");
		assertThat(attributes.getErrorAttributes(new ServletWebRequest(blank), ErrorAttributeOptions.defaults())).doesNotContainKey("message");
	}
}
