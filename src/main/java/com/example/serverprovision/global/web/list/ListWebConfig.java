package com.example.serverprovision.global.web.list;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** {@link ListLinks} 인자 resolver(S8-1) · 상태 변경 뒤 조건 보존 인터셉터(S8-3) 등록. */
@Configuration
@RequiredArgsConstructor
public class ListWebConfig implements WebMvcConfigurer {

    private final ListLinksArgumentResolver listLinksArgumentResolver;
    private final ReturnQueryInterceptor returnQueryInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(listLinksArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(returnQueryInterceptor).addPathPatterns("/management/**");
    }
}
