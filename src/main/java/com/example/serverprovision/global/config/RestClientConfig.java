package com.example.serverprovision.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 전역 {@code RestClient} 빈을 등록하는 설정 클래스이다.
 *
 * <p>역할: Spring Web의 {@code RestClient}를 기본 설정(타임아웃·인터셉터 미적용)으로
 * 빈으로 등록한다. 현재 {@code RHELCompsExtractor}가 이 빈을 주입받아 ISO
 * HTTP URL에서 {@code repomd.xml} 및 {@code comps.xml}을 내려받는 데 사용한다.</p>
 *
 * <p>유스케이스: {@code CompsExtractionService#extractAndSave}가 HTTP URL 기반
 * ISO 경로를 처리할 때 {@code RHELCompsExtractor}가 이 빈을 통해 원격 파일에
 * 접근한다. 로컬 파일 경로의 경우에는 {@code RestClient}를 사용하지 않는다.</p>
 *
 * <p>확장 가이드: 원격 저장소 접근에 인증 헤더·커넥션 타임아웃·재시도 정책이 필요해지면
 * {@code RestClient.builder()}를 사용해 설정을 추가한다. 여러 원격 저장소를 다른 설정으로
 * 접근해야 할 경우 {@code @Qualifier}로 별도 빈을 분리하고 {@code RHELCompsExtractor}
 * 생성자에서 해당 빈을 주입받도록 수정한다.</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
