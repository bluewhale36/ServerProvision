package com.example.serverprovision.provisioning.group.enums;

import com.example.serverprovision.global.web.list.SortField;
import org.springframework.data.domain.Sort;

/** 서버 그룹 목록의 정렬 항목(S8-1) — {@code memberCount} 는 엔티티의 {@code @Formula} 속성이라 SQL 정렬이 된다. */
public enum GroupSortField implements SortField {

    CREATED_AT("createdAt", "만든 시각", Sort.Direction.DESC),
    NAME("name", "이름", Sort.Direction.ASC),
    MEMBER_COUNT("memberCount", "멤버 수", Sort.Direction.DESC);

    private final String property;
    private final String label;
    private final Sort.Direction defaultDirection;

    GroupSortField(String property, String label, Sort.Direction defaultDirection) {
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
