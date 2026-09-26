package com.example.serverprovision.global.web.list;

import org.springframework.data.domain.Sort;

/**
 * 목록 정렬 항목의 화이트리스트(S8-1). 화면별 enum 이 구현하고 {@code sort} 파라미터가 그 상수 이름으로 바인딩된다.
 * 원시 속성명을 URL 에서 받지 않으므로 저장소 내부 속성이 드러나거나 없는 속성으로 실행 시점에 터지는 일이 없다.
 */
public interface SortField {

    /** 정렬에 쓰는 엔티티 속성 경로. */
    String property();

    /** 정렬 셀렉트에 보이는 이름. */
    String label();

    /** {@code dir} 파라미터가 없을 때의 방향 — 시각은 최신순, 이름 · 번호는 오름차순이 자연스럽다. */
    Sort.Direction defaultDirection();
}
