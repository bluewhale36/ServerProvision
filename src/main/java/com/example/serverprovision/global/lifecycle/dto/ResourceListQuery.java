package com.example.serverprovision.global.lifecycle.dto;

import com.example.serverprovision.global.entity.LifecycleEntity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * 자원 관리 목록(Miller 여섯 화면)의 조회 조건(S8-3) — 조회 띠(GET 폼)의 필드가 그대로 바인딩된다.
 *
 * <p>여섯 도메인의 축이 생명주기 플래그(활성 · 사용 중단 · 삭제)로 같아 조건 record 하나를 공유한다. 도메인마다 다른 것은
 * 검색 대상 필드뿐이라 {@link #matches(LifecycleEntity, String...)} 가 그것을 인자로 받는다. 판정은 메모리에서 한다 —
 * 이 화면들은 페이징이 없고 묶음을 이미 메모리에서 만들기 때문에, SQL 로 옮기면 도메인마다 Specification 이 생길 뿐
 * 얻는 것이 없다.</p>
 *
 * <p>활성 · 사용 중단은 <b>effective</b> 값으로 판정한다 — 부모가 비활성이면 자식도 비활성으로 보이는 화면 표시와 같게.</p>
 */
public record ResourceListQuery(
        String q,
        Boolean enabled,
        Boolean deprecated,
        Boolean includeDeleted
) {

    public ResourceListQuery {
        q = (q == null || q.isBlank()) ? null : q.trim();
        includeDeleted = Boolean.TRUE.equals(includeDeleted);
    }

    /** 조건 없는 조회 — 목록 밖의 호출부(보드 선택 폼 등)가 "삭제 제외 전부" 또는 "삭제 포함 전부" 를 표현할 때 쓴다. */
    public static ResourceListQuery unfiltered(boolean includeDeleted) {
        return new ResourceListQuery(null, null, null, includeDeleted);
    }

    /**
     * 조건이 걸렸는가 — 검색어 · 활성 · 사용 중단 중 하나라도. 삭제 포함은 따로 있던 축이라 여기 넣지 않는다.
     * 빈 묶음 숨김(조건 중에만) · 순위 드래그 게이트 · 초기화 링크가 이것을 본다.
     */
    public boolean isFiltered() {
        return q != null || enabled != null || deprecated != null;
    }

    /**
     * 자원 한 건이 조건에 맞는가. 삭제분은 삭제 포함일 때만, 활성 · 사용 중단은 effective 값, 검색어는
     * {@code searchable} 중 하나라도 대소문자 무시 부분 일치(null 은 건너뜀).
     */
    public boolean matches(LifecycleEntity e, String... searchable) {
        if (!includeDeleted && e.isDeleted()) {
            return false;
        }
        if (enabled != null && e.isEnabled() != enabled) {
            return false;
        }
        if (deprecated != null && e.isDeprecated() != deprecated) {
            return false;
        }
        if (q == null) {
            return true;
        }
        String needle = q.toLowerCase(Locale.ROOT);
        return Arrays.stream(searchable).filter(Objects::nonNull)
                .anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(needle));
    }
}
