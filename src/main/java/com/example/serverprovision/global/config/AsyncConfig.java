package com.example.serverprovision.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("file-upload-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                System.err.println("[Async 오류] " + method.getName() + ": " + ex.getMessage());
    }

    // comps 추출 전용 Executor — 추출은 수 분이 걸리는 I/O 작업이므로
    // 파일 업로드와 별도 풀로 분리하여 상호 블로킹을 방지한다.
    // 다중 ISO 동시 추출을 지원하기 위해 corePool 3, 큐 10 으로 설정.
    @Bean(name = "extractionExecutor")
    public Executor extractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("comps-extract-");
        executor.initialize();
        return executor;
    }
}
