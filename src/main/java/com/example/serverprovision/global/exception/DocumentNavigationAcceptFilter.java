package com.example.serverprovision.global.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 문서 이동(페이지 진입)으로 들어온 요청의 {@code Accept} 를 {@code text/html} 로 좁혀 보고한다.
 *
 * <p><b>왜 필요한가.</b> 브라우저는 주소창 이동 · 링크 클릭 · 네이티브 폼 제출에 항상
 * {@code Accept: text/html,…,*}{@code /*;q=0.8} 을 보낸다. 그런데 예외 advice 선택은 Accept 의
 * 선호도(q값)를 보지 않고 등록 순서대로 "요청이 받아줄 수 있는 형식이면 채택" 한다. {@code *}{@code /*} 는
 * 모든 형식과 호환되므로 {@link ApiExceptionHandler} 가 무조건 먼저 합격하고,
 * {@link WebExceptionHandler} 는 실제 브라우저에서 도달할 수 없었다. 그 결과 페이지 이동 중 발생한
 * 도메인 예외의 JSON 응답 본문이 사용자 화면에 그대로 노출됐다(S10 착수 계기).</p>
 *
 * <p><b>왜 이 방식인가.</b> advice 의 {@code @Order} 나 {@code produces} 를 건드리지 않고 협상의
 * <b>입력만</b> 바로잡는다. 조건 분기가 한 줄도 늘지 않으며, 과거 두 차례 사고를 낸 advice 순서 조정을
 * 재현하지 않는다. {@code *}{@code /*} 가 빠진 Accept 에서는 기존 협상이 이미 의도대로 HTML 핸들러를
 * 고르는 것이 실측으로 확인됐다.</p>
 *
 * <p><b>적용 범위.</b> {@code Sec-Fetch-Dest: document} 는 브라우저가 문서 이동에만 보낸다.
 * {@code fetch} · {@code XMLHttpRequest} 는 {@code empty}, PXE 게스트 같은 비브라우저 클라이언트는
 * 이 헤더를 아예 보내지 않으므로 둘 다 JSON 기본값에 그대로 남는다.</p>
 *
 * <p><b>안전 전제.</b> 문서 이동으로 도달하면서 HTML 이 아닌 응답을 내는 엔드포인트가 있으면 이 정규화가
 * 406 을 만든다. 현재 그런 경로는 없다 — 파일 다운로드 엔드포인트가 없고, 비 HTML 생산자는 SSE 스트림
 * ({@code EventSource} 는 {@code Sec-Fetch-Dest: empty})과 PXE 부팅 스크립트(비브라우저)뿐이다.
 * 이 전제는 {@code ErrorResponseChannelTest} 가 회귀 가드로 고정한다.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DocumentNavigationAcceptFilter extends OncePerRequestFilter {

	private static final String SEC_FETCH_DEST = "Sec-Fetch-Dest";
	private static final String DOCUMENT = "document";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
	) throws ServletException, IOException {
		filterChain.doFilter(isDocumentNavigation(request) ? new HtmlAcceptRequest(request) : request, response);
	}

	/** 브라우저가 이 요청을 문서 이동으로 표시했는가. */
	public static boolean isDocumentNavigation(HttpServletRequest request) {
		return DOCUMENT.equalsIgnoreCase(request.getHeader(SEC_FETCH_DEST));
	}

	/**
	 * {@code Accept} 만 {@code text/html} 로 덮어 보고하는 요청 래퍼. 나머지 헤더는 원본 그대로다.
	 * {@code getHeaderNames} 를 건드리지 않는 이유는 Accept 가 이미 목록에 있기 때문이며, 브라우저
	 * 문서 이동에 Accept 가 없는 경우는 없다.
	 */
	private static final class HtmlAcceptRequest extends HttpServletRequestWrapper {

		private HtmlAcceptRequest(HttpServletRequest request) {
			super(request);
		}

		@Override
		public String getHeader(String name) {
			return HttpHeaders.ACCEPT.equalsIgnoreCase(name)
					? MediaType.TEXT_HTML_VALUE
					: super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			return HttpHeaders.ACCEPT.equalsIgnoreCase(name)
					? Collections.enumeration(List.of(MediaType.TEXT_HTML_VALUE))
					: super.getHeaders(name);
		}
	}
}
