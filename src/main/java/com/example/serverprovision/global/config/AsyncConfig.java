package com.example.serverprovision.global.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 비동기 실행기 구성.
 * A1-1 의 comps 추출 파이프라인이 첫 사용자이며, Stage 3 의 프로비저닝 워커가 확장 수혜자로 예정되어 있다.
 * 단일 JVM 기준 풀 크기는 보수적으로 2~4. 대기 큐는 추출이 장시간(수 초~수십 초)이기 때문에 얕게 둔다.
 * Stage S1 의 BackgroundJobService pruner 가 {@code @Scheduled} 을 쓰므로 {@code @EnableScheduling} 도 함께 선언한다.
 *
 * <p>LOG L1 — {@code @Async} 작업이 호출 스레드의 {@link MDC}(requestId) 를 잃지 않도록 {@link TaskDecorator} 로 컨텍스트를 복사한다.
 * 명명 executor({@code compsExtractionExecutor})에는 직접 적용하고, Boot 자동구성 executor({@code applicationTaskExecutor},
 * bare {@code @Async} 의 기본)에는 유일한 {@code TaskDecorator} 빈을 Boot 가 자동 주입한다.</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

	@Bean("compsExtractionExecutor")
	public Executor compsExtractionExecutor(TaskDecorator taskDecorator) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(8);
		executor.setThreadNamePrefix("comps-extract-");
		executor.setTaskDecorator(taskDecorator);
		executor.initialize();
		return executor;
	}

	/**
	 * 호출 스레드의 MDC 컨텍스트를 worker 스레드로 복사하고, 실행 후 원복한다(풀 재사용 누수 방지).
	 * Boot 의 {@code ThreadPoolTaskExecutorBuilder} 가 단일 {@code TaskDecorator} 빈을 {@code applicationTaskExecutor} 에 자동 적용.
	 */
	@Bean
	public TaskDecorator mdcTaskDecorator() {
		return runnable -> {
			Map<String, String> captured = MDC.getCopyOfContextMap();
			return () -> {
				Map<String, String> previous = MDC.getCopyOfContextMap();
				if (captured != null) {
					MDC.setContextMap(captured);
				} else {
					MDC.clear();
				}
				try {
					runnable.run();
				} finally {
					if (previous != null) {
						MDC.setContextMap(previous);
					} else {
						MDC.clear();
					}
				}
			};
		};
	}
}
