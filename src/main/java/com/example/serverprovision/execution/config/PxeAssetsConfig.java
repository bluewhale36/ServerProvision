package com.example.serverprovision.execution.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 진단 리눅스 자산 정적 서빙(E1-1, plan Q1 채택안) — {@code /api/pxe/v1/assets/**} 를
 * {@code pxe.assets.root} 디렉토리에 매핑한다. 전용 스트리밍 컨트롤러 대신 Spring resource handler 를
 * 쓰는 이유: 208MB(modloop)급 정적 파일의 Range 요청 · 경로 이탈 가드({@code PathResourceResolver})를
 * framework 가 이미 제공한다 — 수제 IO 코드는 과잉이다.
 *
 * <p>격리망 실배포에서 외부 httpd 이관이 유리해지면 E1-I 에서 재판단 — URL 원천이
 * {@code pxe.server.base-url} 하나라 이관 비용은 설정 1줄이다.</p>
 */
@Configuration
@ConditionalOnProperty("pxe.assets.root")
@RequiredArgsConstructor
public class PxeAssetsConfig implements WebMvcConfigurer {

    private final PxeAssetsProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/pxe/v1/assets/**")
                .addResourceLocations(properties.getRoot().toUri().toString());
    }
}
