package com.example.serverprovision.global.exception;

import java.util.Map;

/**
 * HTTP 상태 코드의 사용자 노출 문구.
 *
 * <p>HF5 — 종전에는 {@code error.html} 이 {@code ${statusLabel} ?: 'Internal Error'} 로 렌더했다.
 * {@code statusLabel} 은 {@link WebExceptionHandler} 만 채웠으므로, 핸들러가 없는 404 나 바인딩 실패
 * 400 처럼 Spring Boot 기본 {@code /error} 경로로 들어온 요청은 그 값이 비어 fallback 리터럴이 그대로
 * 나왔다. 상태 코드는 맞는데 라벨만 항상 "Internal Error" 라서 "404 Internal Error" 같은 모순된 한 줄이
 * 사용자에게 노출됐다(2026-08-03 MK4 진단).</p>
 *
 * <p>상태 코드에서 문구를 파생하는 단일 소스를 두고 advice 경로({@link WebExceptionHandler})와
 * 기본 {@code /error} 경로({@link ProvisionErrorAttributes})가 함께 참조한다. 상태가 늘어도 표에 항목만
 * 더하면 되며 제어 흐름 분기가 자라지 않는다.</p>
 */
public final class ErrorStatusLabels {

	/**
	 * 표에 없는 상태의 문구. 무슨 일인지 단정하지 않고 결과만 알린다.
	 */
	private static final String FALLBACK = "요청을 처리하지 못했습니다";

	private static final Map<Integer, String> LABELS = Map.ofEntries(
			Map.entry(400, "요청 내용이 올바르지 않습니다"),
			Map.entry(401, "로그인이 필요합니다"),
			Map.entry(403, "접근 권한이 없습니다"),
			Map.entry(404, "페이지를 찾을 수 없습니다"),
			Map.entry(405, "허용되지 않은 요청 방식입니다"),
			Map.entry(408, "요청 시간이 초과되었습니다"),
			Map.entry(409, "다른 작업과 충돌했습니다"),
			Map.entry(413, "요청 용량이 너무 큽니다"),
			Map.entry(415, "지원하지 않는 형식입니다"),
			Map.entry(422, "요청 내용을 처리할 수 없습니다"),
			Map.entry(500, "서버에서 오류가 발생했습니다"),
			Map.entry(502, "연결된 서버가 응답하지 않습니다"),
			Map.entry(503, "서비스를 일시적으로 사용할 수 없습니다"),
			Map.entry(504, "연결된 서버의 응답이 지연되고 있습니다")
	);

	private ErrorStatusLabels() {
	}

	/**
	 * 상태 코드의 한국어 문구. 표에 없으면 {@link #FALLBACK}.
	 */
	public static String of(int status) {
		return LABELS.getOrDefault(status, FALLBACK);
	}
}
