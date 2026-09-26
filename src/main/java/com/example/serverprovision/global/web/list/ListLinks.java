package com.example.serverprovision.global.web.list;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 목록 화면의 링크 조립(S8-1) — 현재 요청의 경로와 질의 파라미터를 들고 있다가 {@code page} 만 바꾼 링크를 낸다.
 *
 * <p>조회 띠는 GET 폼이라 검색 · 필터 · 정렬 · 표시 개수는 브라우저가 질의 문자열로 조립한다. 폼 밖에 남는 링크는
 * 쪽 이동과 초기화 둘뿐이고, 그 둘이 이 record 의 전부다. Thymeleaf 3.1 은 표현식에서 {@code #request} 를 뺐으므로
 * 템플릿이 현재 URL 을 스스로 만들 수 없어 컨트롤러 인자({@link ListLinksArgumentResolver})로 넣어 준다.</p>
 *
 * <p>{@code page} 는 첫 쪽(0)이면 쓰지 않는다 — URL 이 곧 상태 저장소이므로 기본값을 남기지 않는다.</p>
 */
public record ListLinks(String path, Map<String, List<String>> params) {

    private static final String PAGE = "page";

    public static ListLinks of(HttpServletRequest request) {
        Map<String, List<String>> kept = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (!PAGE.equals(name)) {
                kept.put(name, List.of(values));
            }
        });
        // 파라미터 순서를 지킨다 — 쪽마다 링크의 순서가 뒤섞이면 같은 상태인데 URL 이 달라 보인다.
        return new ListLinks(request.getRequestURI(), Collections.unmodifiableMap(kept));
    }

    /** 다른 상태는 그대로 두고 쪽만 바꾼 링크. */
    public String page(int number) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        params.forEach((name, values) -> builder.queryParam(name, values.toArray()));
        if (number > 0) {
            builder.queryParam(PAGE, number);
        }
        return builder.encode().toUriString();
    }

    /**
     * 파라미터 하나를 켜고 끈 링크(S8-2) — 있으면 빼고 없으면 {@code value} 로 넣는다. 쪽은 버린다(첫 쪽).
     * 게스트 서버 묶음 보기의 '등록 진행 중' 펼침처럼 폼 필드가 아닌 화면 상태에 쓴다.
     */
    public String toggle(String name, String value) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        params.forEach((n, values) -> {
            if (!n.equals(name)) {
                builder.queryParam(n, values.toArray());
            }
        });
        if (!params.containsKey(name)) {
            builder.queryParam(name, value);
        }
        return builder.encode().toUriString();
    }

    /** 검색 · 필터 · 정렬 · 쪽을 모두 버린 기본 목록. */
    public String reset() {
        return path;
    }
}
