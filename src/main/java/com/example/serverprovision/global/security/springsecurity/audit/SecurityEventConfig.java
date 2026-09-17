package com.example.serverprovision.global.security.springsecurity.audit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;

/**
 * 인가 거절 이벤트 발행(S20) — 이 빈이 있어야 두 체인의 {@code AuthorizationFilter} 가 {@code AuthorizationDeniedEvent} 를 발행한다
 * (허가 사건은 기본값대로 발행하지 않는다 — 요청마다 한 줄이면 홍수). 인증 이벤트는 Boot 가 등록하는
 * {@code DefaultAuthenticationEventPublisher} 가 웹 체인에, {@code GuestSecurityConfig} 가 게스트 체인의 ProviderManager 에 각각 잇는다.
 */
@Configuration
public class SecurityEventConfig {

    @Bean
    public AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }
}
