package com.example.serverprovision.global.web.list;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

/** 상태 변경 뒤 목록 조건 보존(S8-3 Q1) — 같은 경로 · 허용 키 · 리다이렉트가 이미 가진 키는 컨트롤러 판단. */
class ReturnQueryInterceptorTest {

    private final ReturnQueryInterceptor interceptor = new ReturnQueryInterceptor();

    @Test
    @DisplayName("merge — 조건 · 묶음 선택을 덧붙이고 selectId 는 리다이렉트 쪽을 따른다(돌아올 주소의 옛 선택은 버림)")
    void merge_carriesConditionsNotItemSelection() {
        String merged = ReturnQueryInterceptor.merge(
                "/management/board?selectId=3",
                "/management/board?q=MS%2004&enabled=true&selectKey=GIGABYTE&selectId=9&page=2");

        assertThat(merged).isEqualTo("/management/board?selectId=3&q=MS%2004&enabled=true&selectKey=GIGABYTE");
    }

    @Test
    @DisplayName("merge — 리다이렉트가 이미 가진 키(영구 삭제의 includeDeleted=true 등)는 덮지 않는다")
    void merge_keepsControllerKeys() {
        String merged = ReturnQueryInterceptor.merge(
                "/management/bios?selectBoardId=3&includeDeleted=true",
                "/management/bios?selectBoardId=7&deprecated=true");

        assertThat(merged).isEqualTo("/management/bios?selectBoardId=3&includeDeleted=true&deprecated=true");
    }

    @Test
    @DisplayName("merge — 경로가 다르면 손대지 않는다(다른 화면으로 가는 리다이렉트 · 조작된 값)")
    void merge_otherPath_untouched() {
        assertThat(ReturnQueryInterceptor.merge("/management/board?selectId=3", "/management/os?q=x"))
                .isEqualTo("/management/board?selectId=3");
        assertThat(ReturnQueryInterceptor.merge("/management/board?selectId=3", "https://evil.example/management/board?q=x"))
                .isEqualTo("/management/board?selectId=3&q=x");   // 경로만 같으면 키만 옮긴다 — 목적지는 늘 컨트롤러 경로
    }

    @Test
    @DisplayName("postHandle — 리다이렉트 뷰에만 적용 · returnQuery 가 없거나 뷰가 리다이렉트가 아니면 그대로")
    void postHandle_onlyRedirectViews() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/management/board/3/toggle");
        request.addParameter("returnQuery", "/management/board?q=x");

        ModelAndView redirect = new ModelAndView("redirect:/management/board?selectId=3");
        interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), redirect);
        assertThat(redirect.getViewName()).isEqualTo("redirect:/management/board?selectId=3&q=x");

        ModelAndView page = new ModelAndView("management/board/list");
        interceptor.postHandle(request, new MockHttpServletResponse(), new Object(), page);
        assertThat(page.getViewName()).isEqualTo("management/board/list");

        ModelAndView noReturn = new ModelAndView("redirect:/management/board");
        interceptor.postHandle(new MockHttpServletRequest("POST", "/x"), new MockHttpServletResponse(), new Object(), noReturn);
        assertThat(noReturn.getViewName()).isEqualTo("redirect:/management/board");
    }
}
