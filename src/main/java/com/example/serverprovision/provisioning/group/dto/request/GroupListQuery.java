package com.example.serverprovision.provisioning.group.dto.request;

import com.example.serverprovision.provisioning.group.enums.GroupSortField;
import org.springframework.data.domain.Sort;

/**
 * 서버 그룹 목록의 조회 조건(S8-1) — 조회 띠(GET 폼)의 필드가 그대로 바인딩된다.
 * "전체" 칩의 빈 문자열은 {@code null} 이 되어 술어가 빠지고, 정렬 방향이 없으면 항목의 기본 방향을 쓴다.
 */
public record GroupListQuery(
        String q,
        Boolean hasStandardDefinition,
        GroupSortField sort,
        Sort.Direction dir
) {

    public static final GroupSortField DEFAULT_SORT = GroupSortField.CREATED_AT;

    public GroupListQuery {
        q = (q == null || q.isBlank()) ? null : q.trim();
        sort = sort == null ? DEFAULT_SORT : sort;
        dir = dir == null ? sort.defaultDirection() : dir;
    }

    /** 조건이 하나라도 걸려 있는가 — 빈 결과의 안내 갈래와 초기화 링크 노출이 이것을 본다. */
    public boolean isFiltered() {
        return q != null || hasStandardDefinition != null;
    }
}
