package com.example.serverprovision.global.security.springsecurity.guest.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** {@code @CurrentGuest} 인자 resolver 등록. */
@Configuration
@RequiredArgsConstructor
public class GuestWebConfig implements WebMvcConfigurer {

	private final CurrentGuestArgumentResolver currentGuestArgumentResolver;

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentGuestArgumentResolver);
	}
}
