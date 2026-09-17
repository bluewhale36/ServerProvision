package com.example.serverprovision.global.security.springsecurity.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** 테스트용 — {@link AuthenticationEventLogger} 의 로그 줄을 붙잡는다(RequestCorrelationFilterTest 의 ListAppender 선례). */
public final class AuthLogCapture implements AutoCloseable {

	private final Logger logger = (Logger) LoggerFactory.getLogger(AuthenticationEventLogger.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
	private final Level previous;

	public AuthLogCapture() {
		previous = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		appender.start();
		logger.addAppender(appender);
	}

	public List<ILoggingEvent> events() {
		return appender.list;
	}

	public List<String> lines() {
		return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}

	@Override
	public void close() {
		logger.detachAppender(appender);
		logger.setLevel(previous);
	}
}
