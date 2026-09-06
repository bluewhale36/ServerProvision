package com.example.serverprovision.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 스크립트 요청(XHR)에 내려가는 리다이렉트를 {@code 200 + X-Redirect-Location} 으로 바꾼다(HF9).
 *
 * <p><b>왜 필요한가.</b> S10 전역 폼 가로채기는 제출을 {@code fetch} 로 보낸다. 서버가 PRG(Post-Redirect-Get)로
 * 302 를 주면 fetch 가 그 리다이렉트를 <b>fetch 안에서</b> 따라가고, 목적지 GET 이 flash 를 소비한 채 그 HTML 은
 * 버려진다. 이어지는 {@code location.reload()} 는 빈 손이다 — 성공 안내가 화면에 닿지 않았다(2026-08-13 실측).
 * fetch 는 {@code redirect:'manual'} 로 받아도 Location 을 읽을 수 없으므로, 서버가 리다이렉트를 "따라갈 수 없는
 * 형태" 로 내려 주고 클라이언트가 브라우저 이동으로 목적지를 열게 한다. flash 는 {@code RedirectView} 가
 * 리다이렉트 <b>직전에</b> 세션에 저장하므로 변환과 무관하게 보존된다.</p>
 *
 * <p><b>적용 범위.</b> {@code X-Requested-With: XMLHttpRequest} 를 보낸 요청만 — 폼 가로채기가 이미 보내는 표식이며
 * {@code BiosMetadataController} 등이 AJAX 판정에 쓰는 것과 같다. 문서 이동 · curl · PXE 게스트는 302 를 그대로 받는다.
 * 리다이렉트가 만들어지는 두 방식({@code sendRedirect} · {@code setStatus(3xx) + Location})을 모두 덮는다.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class XhrRedirectFilter extends OncePerRequestFilter {

	public static final String REDIRECT_HEADER = "X-Redirect-Location";
	private static final String REQUESTED_WITH = "X-Requested-With";
	private static final String XML_HTTP_REQUEST = "XMLHttpRequest";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
	) throws ServletException, IOException {
		filterChain.doFilter(request, isXhr(request) ? new XhrRedirectResponse(response) : response);
	}

	/** 클라이언트가 스스로 스크립트 요청이라고 밝혔는가. */
	public static boolean isXhr(HttpServletRequest request) {
		return XML_HTTP_REQUEST.equalsIgnoreCase(request.getHeader(REQUESTED_WITH));
	}

	/**
	 * 리다이렉트를 200 + 헤더로 바꾸는 응답 래퍼. 나머지 동작은 원본 그대로다.
	 * {@code setStatus(3xx)} 뒤 {@code Location} 이 오는 순서(Spring {@code RedirectView} 의 http11 경로)와
	 * {@code sendRedirect} 둘 다 같은 결과를 낸다.
	 */
	static final class XhrRedirectResponse extends HttpServletResponseWrapper {

		private boolean redirectStatusPending;

		XhrRedirectResponse(HttpServletResponse response) {
			super(response);
		}

		@Override
		public void sendRedirect(String location) throws IOException {
			convert(location);
		}

		@Override
		public void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException {
			convert(location);
		}

		@Override
		public void setStatus(int sc) {
			if (isRedirect(sc)) {
				redirectStatusPending = true;   // Location 이 뒤따르면 그때 변환한다
				return;
			}
			redirectStatusPending = false;
			super.setStatus(sc);
		}

		@Override
		public void setHeader(String name, String value) {
			if (redirectStatusPending && HttpHeaders.LOCATION.equalsIgnoreCase(name)) {
				convertQuietly(value);
				return;
			}
			super.setHeader(name, value);
		}

		@Override
		public void addHeader(String name, String value) {
			if (redirectStatusPending && HttpHeaders.LOCATION.equalsIgnoreCase(name)) {
				convertQuietly(value);
				return;
			}
			super.addHeader(name, value);
		}

		private void convert(String location) throws IOException {
			convertQuietly(location);
			flushBuffer();   // sendRedirect 처럼 응답을 확정한다 — 뒤에 무언가 더 쓰이지 않게
		}

		private void convertQuietly(String location) {
			redirectStatusPending = false;
			super.setStatus(SC_OK);
			super.setHeader(REDIRECT_HEADER, location);
			super.setContentLength(0);
		}

		private static boolean isRedirect(int sc) {
			return sc >= 300 && sc < 400;
		}
	}
}
