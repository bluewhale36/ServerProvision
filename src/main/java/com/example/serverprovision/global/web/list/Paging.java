package com.example.serverprovision.global.web.list;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 페이징의 한 자리(S8-1). page · size 는 Spring Data 의 {@code Pageable} 리졸버가 정정해 준 값을 그대로 쓰고,
 * 정렬만 화이트리스트({@link SortField})로 다시 만든다.
 *
 * <p>리졸버는 {@code sort} 파라미터를 원시 속성명으로 읽어 {@code Sort} 를 만들어 두는데, 그 값은 여기서 버린다.
 * 원시 속성 정렬이 저장소에 닿을 길은 이 한 곳에서 막힌다 — 컨트롤러가 {@code pageable} 을 그대로 저장소에
 * 넘기지 않고 반드시 이 메서드를 거친다.</p>
 *
 * <p>정렬 값이 같은 행끼리는 {@code id} 로 한 번 더 줄 세운다(CP5 O-10). 순서가 정해지지 않으면 DB 가 쪽마다 다른
 * 순서로 돌려줄 수 있고, 그러면 쪽 경계에서 같은 행이 두 번 보이거나 한 행이 빠진다.</p>
 */
public final class Paging {

    /** 기본 표시 개수 — {@code @PageableDefault(size)} 와 표시 개수 셀렉트의 기본값이 이 값을 본다. */
    public static final int DEFAULT_SIZE = 20;

    private static final String TIE_BREAKER = "id";

    private Paging() {
    }

    public static PageRequest of(Pageable pageable, SortField field, Sort.Direction direction) {
        int page = pageable.isPaged() ? pageable.getPageNumber() : 0;
        int size = pageable.isPaged() ? pageable.getPageSize() : DEFAULT_SIZE;
        Sort sort = Sort.by(direction, field.property());
        if (!TIE_BREAKER.equals(field.property())) {
            sort = sort.and(Sort.by(direction, TIE_BREAKER));
        }
        return PageRequest.of(page, size, sort);
    }
}
