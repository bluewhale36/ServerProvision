package com.example.serverprovision.execution.enums;

import com.example.serverprovision.global.web.list.SortField;
import org.springframework.data.domain.Sort;

/** 게스트 서버 표 보기의 정렬 항목(S8-2) — {@code sort} 파라미터의 화이트리스트. 라벨은 서버 표 머리글 어휘와 같다. */
public enum ServerSortField implements SortField {

    CREATED_AT("createdAt", "등록 시각", Sort.Direction.DESC),
    NAME("name", "이름", Sort.Direction.ASC),
    LAST_SEEN_AT("lastSeenAt", "마지막 접촉", Sort.Direction.DESC);

    private final String property;
    private final String label;
    private final Sort.Direction defaultDirection;

    ServerSortField(String property, String label, Sort.Direction defaultDirection) {
        this.property = property;
        this.label = label;
        this.defaultDirection = defaultDirection;
    }

    @Override
    public String property() {
        return property;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public Sort.Direction defaultDirection() {
        return defaultDirection;
    }
}
