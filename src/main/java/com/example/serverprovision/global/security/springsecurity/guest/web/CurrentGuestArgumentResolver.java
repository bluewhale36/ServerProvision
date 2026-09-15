package com.example.serverprovision.global.security.springsecurity.guest.web;

import com.example.serverprovision.global.security.springsecurity.exception.GuestUnauthenticatedException;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentGuest GuestPrincipal} resolver(S19-1 D-6) — SecurityContext 에서 읽는다. {@code @AuthenticationPrincipal} 은 보안 MVC 통합이
 * 없는 슬라이스 테스트에서 해석되지 않으므로 앱의 인자 resolver 로 둔다(WebMvcConfigurer 로 등록 · 슬라이스에도 포함된다).
 */
@Component
public class CurrentGuestArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentGuest.class) && GuestPrincipal.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
	                              WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof GuestPrincipal guest) {
			return guest;
		}
		throw new GuestUnauthenticatedException();
	}
}
