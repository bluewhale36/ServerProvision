package com.example.serverprovision.global.web;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** HF9 — XHR 요청의 리다이렉트만 200 + X-Redirect-Location 으로 바뀌고, 그 밖은 원본 그대로다. */
class XhrRedirectFilterTest {

    private final XhrRedirectFilter filter = new XhrRedirectFilter();

    private static MockHttpServletRequest xhr() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/system/asset/recheck");
        req.addHeader("X-Requested-With", "XMLHttpRequest");
        return req;
    }

    @Test
    @DisplayName("XHR + sendRedirect → 200 · X-Redirect-Location · 본문 없음 · Location 없음")
    void xhrSendRedirect() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(xhr(), res, (FilterChain) (rq, rs) ->
                ((HttpServletResponse) rs).sendRedirect("/system/asset"));

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader(XhrRedirectFilter.REDIRECT_HEADER)).isEqualTo("/system/asset");
        assertThat(res.getHeader("Location")).isNull();
        assertThat(res.getContentAsByteArray()).isEmpty();
        assertThat(res.isCommitted()).isTrue();
    }

    @Test
    @DisplayName("XHR + setStatus(302) 뒤 Location(RedirectView 의 http11 경로) → 같은 결과")
    void xhrStatusAndLocation() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(xhr(), res, (FilterChain) (rq, rs) -> {
            HttpServletResponse h = (HttpServletResponse) rs;
            h.setStatus(303);
            h.setHeader("Location", "/maintenance/reconciliation");
        });

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader(XhrRedirectFilter.REDIRECT_HEADER)).isEqualTo("/maintenance/reconciliation");
        assertThat(res.getHeader("Location")).isNull();
    }

    @Test
    @DisplayName("비 XHR(문서 이동 · curl) 의 리다이렉트는 302 + Location 그대로")
    void nonXhrUntouched() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/system/asset/recheck");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (FilterChain) (rq, rs) ->
                ((HttpServletResponse) rs).sendRedirect("/system/asset"));

        assertThat(res.getStatus()).isEqualTo(302);
        assertThat(res.getRedirectedUrl()).isEqualTo("/system/asset");
        assertThat(res.getHeader(XhrRedirectFilter.REDIRECT_HEADER)).isNull();
    }

    @Test
    @DisplayName("XHR 인데 리다이렉트가 아닌 응답(200 JSON · 409)은 손대지 않는다 — 3xx 아닌 setStatus 는 그대로 통과")
    void xhrNonRedirectPassesThrough() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(xhr(), res, (FilterChain) (rq, rs) -> {
            HttpServletResponse h = (HttpServletResponse) rs;
            h.setStatus(409);
            h.setHeader("Content-Type", "application/json");
            h.getWriter().write("{\"message\":\"거절\"}");
        });

        assertThat(res.getStatus()).isEqualTo(409);
        assertThat(res.getHeader(XhrRedirectFilter.REDIRECT_HEADER)).isNull();
        assertThat(res.getContentAsString()).contains("거절");
    }
}
