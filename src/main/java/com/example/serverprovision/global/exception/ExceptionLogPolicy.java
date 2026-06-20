package com.example.serverprovision.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * LOG L2 — advice 예외 로깅의 레벨 결정 SSOT.
 *
 * <p>예외 → 로그 레벨 매핑을 if-else 분기가 아니라 HTTP status 의 {@link HttpStatus#is5xxServerError()} 다형성으로 결정한다.
 * {@link ApiExceptionHandler}(JSON)와 {@link WebExceptionHandler}(HTML) 양 advice 가 이 단일 진입점을 공유해
 * "Api/Web 복붙 드리프트"를 차단한다 — 한쪽만 레벨이 바뀌는 사고(과거 content-negotiation v1~v3 류)를 구조적으로 막는다.</p>
 *
 * <ul>
 *   <li><b>4xx</b>(예상된 사용자 실패 — NotFound/Conflict/도메인 가드) → <b>WARN</b>, stack 없음(alert fatigue 회피).</li>
 *   <li><b>5xx</b>(작업 완료 불가 — IO/저장 실패) → <b>ERROR</b> + throwable(stack). 고우선 알림.</li>
 * </ul>
 *
 * <p>상태 없는 정책이라 정적 유틸로 둔다 — 양 advice 가 생성자 의존성 없이 호출하므로 {@code @WebMvcTest} 슬라이스가
 * 별도 빈 등록 없이 advice 를 그대로 적재한다.</p>
 *
 * <p>마스킹 : 예외 메시지에 사용자 입력(typedName 등)이 섞일 수 있어 메시지 본문은 로그에 싣지 않는다 —
 * 자원 컨텍스트는 L3 의 throw-site {@code guard.*} 로그(resource={TYPE}#{id})와 L1 canonical line(path)이 제공한다.</p>
 */
public final class ExceptionLogPolicy {

	private static final Logger log = LoggerFactory.getLogger("exception-advice");

	private ExceptionLogPolicy() {
	}

	/**
	 * 예외 1건을 레벨 규율에 따라 1회 로깅한다.
	 *
	 * @param eventCode advice.&lt;op&gt; (예 advice.notFound, advice.conflict, advice.domain.unmapped)
	 * @param ex        대상 예외
	 * @param status    응답 status — 5xx 면 ERROR+stack, 그 외 WARN
	 * @param variant   응답 채널(json | html) — 동일 eventCode 의 Api/Web 구분
	 */
	public static void record(String eventCode, Throwable ex, HttpStatus status, String variant) {
		String type = ex.getClass().getSimpleName();
		if (status.is5xxServerError()) {
			log.error("[{}] type={} status={} variant={} outcome={}",
					eventCode, type, status.value(), variant, status.value(), ex);
		} else {
			log.warn("[{}] type={} status={} variant={} outcome={}",
					eventCode, type, status.value(), variant, status.value());
		}
	}
}
