package com.example.serverprovision.global.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Spring Boot 기본 {@code /error} 경로의 오류 속성에 {@code statusLabel} 을 채운다.
 *
 * <p>HF5 — {@link WebExceptionHandler} 는 자기가 잡은 예외에만 {@code statusLabel} 을 넣는다. 핸들러가
 * 없는 404, 바인딩 실패 400 처럼 advice 를 거치지 않고 기본 {@code /error} 로 흐르는 요청은 그 값이 비어
 * {@code error.html} 의 fallback 리터럴("Internal Error")이 상태와 무관하게 노출됐다. 이 확장점은 Spring 이
 * 제공하는 {@link DefaultErrorAttributes} 대체 지점이며, 두 경로가 같은 {@link ErrorStatusLabels} 를 보게 해
 * 문구를 일치시킨다.</p>
 *
 * <p>속성을 더하기만 하므로 JSON 오류 응답에도 {@code statusLabel} 이 함께 실린다. 기존 필드는 그대로라
 * 이를 읽던 클라이언트에는 영향이 없다.</p>
 */
@Component
public class ProvisionErrorAttributes extends DefaultErrorAttributes {

	@Override
	public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
		Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
		attributes.put("statusLabel", ErrorStatusLabels.of(resolveStatus(attributes.get("status"))));
		return attributes;
	}

	/**
	 * 기본 구현이 status 를 못 넣는 드문 경우(속성 옵션 제한 등)까지 안전하게 500 으로 수렴시킨다.
	 */
	private static int resolveStatus(Object status) {
		return (status instanceof Integer value) ? value : 500;
	}
}
