package com.example.serverprovision.global.web.list;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** {@link ListLinks} 인자 resolver 등록(S8-1). */
@Configuration
@RequiredArgsConstructor
public class ListWebConfig implements WebMvcConfigurer {

    private final ListLinksArgumentResolver listLinksArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(listLinksArgumentResolver);
    }
}
