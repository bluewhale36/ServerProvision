package com.example.serverprovision.provisioning.setting.enums;

import com.example.serverprovision.global.web.list.SortField;
import org.springframework.data.domain.Sort;

/** 세팅 정의서 목록의 정렬 항목(S8-1) — {@code sort} 파라미터의 화이트리스트. */
public enum SettingSortField implements SortField {

    ID("id", "번호", Sort.Direction.ASC),
    NAME("name", "이름", Sort.Direction.ASC),
    CREATED_AT("createdAt", "생성일시", Sort.Direction.DESC);

    private final String property;
    private final String label;
    private final Sort.Direction defaultDirection;

    SettingSortField(String property, String label, Sort.Direction defaultDirection) {
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
