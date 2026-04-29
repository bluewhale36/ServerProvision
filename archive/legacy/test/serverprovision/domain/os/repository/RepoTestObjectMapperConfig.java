package com.example.serverprovision.domain.os.repository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 레포지토리 {@code @DataJpaTest} 슬라이스에서 사용하는 ObjectMapper 제공용 테스트 설정.
 *
 * <p>{@code SettingProcessConverter} 가 {@code @Converter(autoApply=true)} 로 선언되어 있어
 * JPA 메타모델 스캔 시 {@link ObjectMapper} 빈이 요구된다. {@code @DataJpaTest} 슬라이스는
 * Jackson 자동구성을 포함하지 않으므로 테스트 전용으로 수동 제공한다.
 * 외부 {@code @Configuration} 클래스로 분리해 {@code @DataJpaTest} 의 패키지 스캐닝을 방해하지 않는다.</p>
 */
@Configuration
public class RepoTestObjectMapperConfig {

    @Bean
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper();
    }
}
