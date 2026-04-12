package com.example.serverprovision.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

/**
 * 비동기 실행 환경을 구성하는 설정 클래스이다.
 *
 * <p>역할: {@code @EnableAsync}를 활성화하고 두 개의 스레드 풀을 등록한다.
 * {@code AsyncConfigurer} 기본 Executor(스레드명 접두사 {@code file-upload-})는
 * {@code @Async} 어노테이션이 붙은 메소드의 기본 실행기로 사용되며,
 * {@code extractionExecutor}(스레드명 접두사 {@code comps-extract-})는
 * comps 추출 전용으로 {@code @Qualifier("extractionExecutor")}로 명시 주입된다.</p>
 *
 * <p>유스케이스: {@code FileUploadService#saveAsync}가 기본 Executor를 통해 ISO 파일을
 * 비동기로 서버 저장 경로로 이동시킨다. {@code ExtractionTaskService#startExtraction}은
 * {@code extractionExecutor}를 통해 수 분이 소요되는 {@code comps.xml} 파싱 작업을
 * 별도 풀에서 실행해 파일 업로드 풀과의 상호 블로킹을 방지한다. 두 풀의 분리로
 * 대용량 ISO 업로드와 동시 comps 추출이 독립적으로 진행될 수 있다.</p>
 *
 * <p>확장 가이드: 새로운 장시간 비동기 작업(예: Kickstart 스크립트 배포, IPMI 원격 제어)이
 * 추가될 경우 해당 작업 특성에 맞는 별도 {@code @Bean} Executor를 이 클래스에 추가하고,
 * 호출부에서 {@code @Qualifier}로 명시 주입한다. 파풀 크기를 조정할 때는 동시 ISO 작업 수와
 * 서버 메모리를 함께 고려한다.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * {@code @Async} 기본 Executor이다.
     *
     * <p>역할: {@code FileUploadService#saveAsync}처럼 {@code @Async}만 붙은
     * 메소드가 사용하는 기본 스레드 풀이다. corePool 2, maxPool 5, 큐 10으로 설정된다.</p>
     *
     * <p>유스케이스: 파일 업로드 후 Spring 임시 파일을 서버 경로로 이동하는 I/O 작업에
     * 사용된다. 동시 업로드가 많아지면 큐에 쌓이며, maxPool(5)까지 스레드가 확장된다.</p>
     *
     * <p>확장 가이드: 업로드 동시성을 높여야 할 경우 {@code maxPoolSize}를 조정한다.
     * 특정 작업에 별도 풀이 필요하면 {@code extractionExecutor}처럼 named {@code @Bean}으로
     * 분리하고 {@code @Qualifier}로 주입한다.</p>
     */
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

    /**
     * comps 추출 전용 Executor이다.
     *
     * <p>역할: {@code comps.xml} 파싱 같은 수 분 소요 I/O 작업을 파일 업로드 풀과
     * 분리하여 상호 블로킹을 방지한다. corePool 3, maxPool 3(고정), 큐 10으로 설정되어
     * 최대 3개의 ISO에 대한 동시 추출을 지원한다.</p>
     *
     * <p>유스케이스: {@code ExtractionTaskService#startExtraction}이 이 빈을
     * {@code @Qualifier("extractionExecutor")}로 주입받아 {@code runExtraction} 람다를
     * 제출한다. 각 추출 태스크는 {@code ExtractionTask#update}를 통해 진행률을 공유하며
     * HTTP 폴링 엔드포인트에서 조회된다.</p>
     *
     * <p>확장 가이드: 동시 추출 수를 늘리려면 {@code corePoolSize}와 {@code maxPoolSize}를
     * 동일하게 조정한다(고정 풀 의도). 추출 외 장시간 작업이 추가될 때는 별도 Executor를
     * 새로 등록해 이 풀과 격리한다.</p>
     */
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
