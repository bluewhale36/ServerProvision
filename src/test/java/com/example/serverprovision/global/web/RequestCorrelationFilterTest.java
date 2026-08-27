package com.example.serverprovision.global.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요청 경계 canonical line 의 레벨 규칙 — 게스트 상시 폴링(30초 주기)의 성공 라인은 DEBUG 로 내려
 * 완주 뒤 회수 전 게스트가 로그를 밀어내지 않게 한다(2026-08-27 실기, decisions 11 ⓑ).
 */
class RequestCorrelationFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();
    private Level previous;

    @BeforeEach
    void attach() {
        previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(previous);
    }

    @Test
    @DisplayName("게스트 폴링(boot GET · checkin POST)의 성공 완료 라인은 DEBUG, 일반 화면 요청은 INFO")
    void guestPolling_completeLineIsDebug() throws Exception {
        run("GET", "/api/pxe/v1/boot", 200);
        run("POST", "/api/pxe/v1/agent/checkin", 200);
        run("GET", "/provisioning/server", 200);

        List<ILoggingEvent> complete = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("http.request.complete")).toList();
        assertThat(complete).hasSize(3);
        assertThat(complete.get(0).getLevel()).isEqualTo(Level.DEBUG);
        assertThat(complete.get(1).getLevel()).isEqualTo(Level.DEBUG);
        assertThat(complete.get(2).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("폴링이라도 실패(4xx)는 진단 대상 — INFO 를 유지한다")
    void guestPolling_failureStaysInfo() throws Exception {
        run("GET", "/api/pxe/v1/boot", 404);

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getFormattedMessage()).contains("http.request.complete").contains("status=404");
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
        });
    }

    private void run(String method, String uri, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        filter.doFilter(request, response, new MockFilterChain());
    }
}
