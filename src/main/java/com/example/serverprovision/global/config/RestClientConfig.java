package com.example.serverprovision.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 전역 {@link RestClient} 빈. comps.xml / repomd.xml 을 HTTP 로 읽어오는 추출 전략에서 사용한다.
 * 현재는 기본 설정만 주입. 타임아웃·인터셉터·재시도가 필요해지면 {@code RestClient.builder()} 로 확장한다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
