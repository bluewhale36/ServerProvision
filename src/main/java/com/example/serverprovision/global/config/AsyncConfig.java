package com.example.serverprovision.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 실행기 구성.
 * A1-1 의 comps 추출 파이프라인이 첫 사용자이며, Stage 3 의 프로비저닝 워커가 확장 수혜자로 예정되어 있다.
 * 단일 JVM 기준 풀 크기는 보수적으로 2~4. 대기 큐는 추출이 장시간(수 초~수십 초)이기 때문에 얕게 둔다.
 * Stage S1 의 BackgroundJobService pruner 가 {@code @Scheduled} 을 쓰므로 {@code @EnableScheduling} 도 함께 선언한다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

	@Bean("compsExtractionExecutor")
	public Executor compsExtractionExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(8);
		executor.setThreadNamePrefix("comps-extract-");
		executor.initialize();
		return executor;
	}
}
