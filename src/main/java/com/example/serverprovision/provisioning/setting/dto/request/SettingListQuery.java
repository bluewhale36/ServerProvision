package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.enums.SettingSortField;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.BindParam;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 세팅 정의서 목록의 조회 조건(S8-1) — 조회 띠(GET 폼)의 필드가 그대로 바인딩된다.
 *
 * <p>"전체" 칩은 빈 문자열을 보내고 Spring 기본 컨버터가 그것을 {@code null} 로 바꾼다 — 축이 {@code null} 이면
 * 술어가 빠진다. 단계 칩은 체크박스라 값이 반복되고({@code processType=A&processType=B}) 하나도 없으면 빈 집합이다.
 * 정렬 방향이 없으면 항목의 기본 방향을 쓴다.</p>
 */
public record SettingListQuery(
        String q,
        Boolean enabled,
        Boolean deprecated,
        @BindParam("processType") Set<SettingProcessType> processTypes,
        Boolean includeDeleted,
        SettingSortField sort,
        Sort.Direction dir
) {

    public static final SettingSortField DEFAULT_SORT = SettingSortField.ID;

    public SettingListQuery {
        q = (q == null || q.isBlank()) ? null : q.trim();
        // 주소창에 빈 값(processType=)을 직접 넣으면 그 원소가 null 로 바인딩된다 — 걸러 내고 담는다.
        EnumSet<SettingProcessType> types = EnumSet.noneOf(SettingProcessType.class);
        if (processTypes != null) {
            processTypes.stream().filter(Objects::nonNull).forEach(types::add);
        }
        processTypes = Collections.unmodifiableSet(types);
        includeDeleted = Boolean.TRUE.equals(includeDeleted);
        sort = sort == null ? DEFAULT_SORT : sort;
        dir = dir == null ? sort.defaultDirection() : dir;
    }

    /** 조건이 하나라도 걸려 있는가 — 빈 결과의 안내 갈래와 초기화 링크 노출이 이것을 본다. */
    public boolean isFiltered() {
        return q != null || enabled != null || deprecated != null || !processTypes.isEmpty() || includeDeleted;
    }
}
