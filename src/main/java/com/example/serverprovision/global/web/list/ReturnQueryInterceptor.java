package com.example.serverprovision.global.web.list;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Set;

/**
 * 상태 변경 뒤 목록 조회 조건 보존(S8-3 Q1). 목록 화면의 상태 변경 폼(활성화 · 사용 중단 · 삭제 · 복구 · 영구 삭제)은
 * 제출 때 {@value #PARAM} 에 지금 보던 목록 주소를 싣는다(list-query.js). 컨트롤러가 목록으로 리다이렉트하면
 * 여기서 그 주소의 조건 · 묶음 선택 파라미터를 리다이렉트 주소에 덧붙인다.
 *
 * <p>컨트롤러마다 리다이렉트에 인자를 하나씩 늘리는 대신 여기 한 곳에서 붙인다 — 자원 도메인 여섯 × 조작 대여섯의
 * 반환문을 모두 고치면 새 조작이 생길 때마다 같은 누락이 반복된다.</p>
 *
 * <p>규칙 — 경로가 같을 때만(다른 화면으로 가는 리다이렉트는 건드리지 않는다), 허용한 키만, 리다이렉트 주소에 이미
 * 있는 키는 컨트롤러 판단을 따른다. {@code selectId} 는 허용하지 않는다 — 어떤 항목을 고를지는 조작 결과(영구
 * 삭제면 선택 없음)라 컨트롤러가 정한다. 경로를 {@value #PARAM} 에서 가져오지 않으므로 열린 리다이렉트가 생기지 않는다.</p>
 */
@Component
public class ReturnQueryInterceptor implements HandlerInterceptor {

    static final String PARAM = "returnQuery";

    private static final String REDIRECT = "redirect:";

    /** 목록 조건(검색 · 활성 · 사용 중단 · 삭제 포함)과 묶음 선택(C1 · 종류). */
    private static final Set<String> CARRIED = Set.of(
            "q", "enabled", "deprecated", "includeDeleted", "selectKey", "selectBoardId", "selectKind");

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        String returnQuery = request.getParameter(PARAM);
        if (modelAndView == null || returnQuery == null || returnQuery.isBlank()) {
            return;
        }
        String viewName = modelAndView.getViewName();
        if (viewName == null || !viewName.startsWith(REDIRECT)) {
            return;
        }
        modelAndView.setViewName(REDIRECT + merge(viewName.substring(REDIRECT.length()), returnQuery));
    }

    static String merge(String target, String returnQuery) {
        UriComponents to = UriComponentsBuilder.fromUriString(target).build();
        UriComponents from = UriComponentsBuilder.fromUriString(returnQuery).build();
        if (from.getPath() == null || !from.getPath().equals(to.getPath())) {
            return target;
        }
        UriComponentsBuilder merged = UriComponentsBuilder.fromUriString(target);
        from.getQueryParams().forEach((name, values) -> {
            if (CARRIED.contains(name) && !to.getQueryParams().containsKey(name)) {
                merged.queryParam(name, List.copyOf(values).toArray());
            }
        });
        return merged.build().toUriString();
    }
}
