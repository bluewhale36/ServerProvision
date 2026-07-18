package com.example.serverprovision.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * LOG L1 — 요청 상관관계 필터.
 *
 * <p>한 HTTP 요청에서 발생하는 모든 로그(동기 + {@code @Async} 전파)를 하나의 {@code requestId} 로 묶는다.
 * 인바운드 {@code X-Request-Id} 헤더가 있으면 그 값을, 없으면 UUID 를 생성해 {@link MDC} 에 심고, 응답 헤더에도 반영한다.
 * {@code finally} 에서 MDC 를 제거해 스레드풀 재사용 시 누수를 막는다 — logback {@code %X{requestId}} 패턴이 자동 방출.</p>
 *
 * <p>요청 경계마다 canonical 한 줄({@code http.request.complete})로 method·path·status·소요시간을 요약하고,
 * 두 advice 를 모두 빠져나간 미처리 예외는 최후 안전망({@code http.request.unhandled} ERROR)으로 잡아 stack 과 함께 남긴 뒤 재던진다.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	private static final String HEADER = "X-Request-Id";
	private static final String MDC_KEY = "requestId";

	/**
	 * 핸들러가 이 요청 속성을 {@code true} 로 세우면 경계 canonical line({@code http.request.complete})을
	 * 생략한다 — 상시 폴링(예: idle 상태의 {@code /jobs} 새로고침)이 로그를 밀어내는 것을 막는 억제 훅.
	 * 단 5xx 는 진단이 필요하므로 억제되지 않는다(아래 {@link #logComplete}).
	 */
	public static final String SUPPRESS_ACCESS_LINE = "log.suppressAccessLine";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = headerOrNew(request.getHeader(HEADER));
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		long startNanos = System.nanoTime();
		try {
			// 상시 폴링 GET(/jobs)·정적 리소스(.js/.css/...)의 진입 로그는 남기지 않는다 — 노이즈.
			// (폴링은 진입 시점에 활성 Job 여부를 모르고, 정적 리소스는 성공 시 관심 대상이 아니다.)
			// method·path·status 는 complete 라인이 담고, 정적 리소스 실패(4xx/5xx)는 아래에서 남긴다.
			if (log.isDebugEnabled() && !isPollingRequest(request) && !isStaticResource(request)) {
				log.debug("[http.request.start] method={} path={}", request.getMethod(), path(request));
			}
			chain.doFilter(request, response);
			logComplete(request, response.getStatus(), startNanos);
		} catch (Exception e) {
			// 두 advice 를 빠져나간 미처리 예외의 최후 안전망 — advice.unexpected 와 이중화. stack 포함.
			log.error("[http.request.unhandled] method={} path={} durationMs={} outcome=failed",
					request.getMethod(), path(request), elapsedMs(startNanos), e);
			throw e;
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	/** 상시 폴링 GET(알림 센터의 {@code /jobs}·{@code /jobs/{id}} 새로고침). dismiss(POST)·기타는 대상 아님. */
	private static boolean isPollingRequest(HttpServletRequest request) {
		return "GET".equals(request.getMethod()) && request.getRequestURI().startsWith("/jobs");
	}

	/** 확장자로 판정하는 정적 리소스(css/js/폰트/이미지 등). 경로 prefix 규약과 독립 — 실 위치가 여러 곳이다. */
	private static final Set<String> STATIC_EXTENSIONS = Set.of(
			".js", ".css", ".map", ".ico", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp",
			".woff", ".woff2", ".ttf", ".eot");

	private static boolean isStaticResource(HttpServletRequest request) {
		String uri = request.getRequestURI();
		int dot = uri.lastIndexOf('.');
		return dot >= 0 && STATIC_EXTENSIONS.contains(uri.substring(dot).toLowerCase());
	}

	/** 정적 리소스는 상관관계 대상 아님 — 도메인/PXE 엔드포인트는 제외하지 않는다. */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String p = request.getRequestURI();
		return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/static/")
				|| p.startsWith("/images/") || p.equals("/favicon.ico");
	}

	/**
	 * 요청 경계 canonical line. 5xx 는 advice 가 처리 못 한 미처리 500 도 requestId·path 와 함께 가시화하기 위해 WARN,
	 * 그 외는 INFO. (advice 가 처리한 5xx 는 advice.* ERROR+stack 으로 별도 1회 — eventCode 가 달라 중복 아님.)
	 */
	private void logComplete(HttpServletRequest request, int status, long startNanos) {
		// 정적 리소스는 성공(2xx/3xx) 시 남기지 않는다 — 실패(4xx/5xx: 파일 없음·서빙 오류)만 가시화.
		if (status < 400 && isStaticResource(request)) {
			return;
		}
		// 핸들러가 억제를 요청한 idle 폴링은 남기지 않는다 — 단 5xx 는 항상 가시화.
		if (status < 500 && Boolean.TRUE.equals(request.getAttribute(SUPPRESS_ACCESS_LINE))) {
			return;
		}
		String fmt = "[http.request.complete] method={} path={} status={} durationMs={}";
		Object[] args = {request.getMethod(), path(request), status, elapsedMs(startNanos)};
		if (status >= 500) {
			log.warn(fmt, args);
		} else {
			log.info(fmt, args);
		}
	}

	private static String headerOrNew(String header) {
		return (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header;
	}

	private static String path(HttpServletRequest request) {
		String query = request.getQueryString();
		return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}
}
