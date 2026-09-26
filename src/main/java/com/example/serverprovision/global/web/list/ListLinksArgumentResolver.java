package com.example.serverprovision.global.web.list;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 인자 {@link ListLinks} 해석(S8-1). 인자를 선언한 목록 컨트롤러에서만 돌고, {@code @WebMvcTest} 슬라이스에도
 * {@code HandlerMethodArgumentResolver} 빈으로 포함된다({@code CurrentGuestArgumentResolver} 와 같은 등록 방식).
 */
@Component
public class ListLinksArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ListLinks.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("ListLinks 는 서블릿 요청에서만 만들 수 있습니다.");
        }
        return ListLinks.of(request);
    }
}
