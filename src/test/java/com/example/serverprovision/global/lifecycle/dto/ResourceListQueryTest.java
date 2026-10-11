package com.example.serverprovision.global.lifecycle.dto;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 자원 관리 목록의 조회 조건(S8-3) — 기본값 · 조건 여부 · 한 건 판정. */
class ResourceListQueryTest {

    private static BoardModel board(boolean enabled, boolean deprecated, boolean deleted) {
        return BoardModel.builder()
                .id(1L).vendor(Vendor.GIGABYTE).modelName("MS04-CE0")
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted).build();
    }

    @Test
    @DisplayName("압축 생성자 — 공백 검색어는 null · 앞뒤 공백 제거 · includeDeleted null 은 false")
    void compactConstructor_normalizes() {
        assertThat(new ResourceListQuery("   ", null, null, null).q()).isNull();
        assertThat(new ResourceListQuery("  ms04 ", null, null, null).q()).isEqualTo("ms04");
        assertThat(new ResourceListQuery(null, null, null, null).includeDeleted()).isFalse();
        assertThat(new ResourceListQuery(null, null, null, null)).isEqualTo(ResourceListQuery.unfiltered(false));
    }

    @Test
    @DisplayName("isFiltered — 검색어 · 활성 · 사용 중단 중 하나라도. 삭제 포함은 조건이 아니다")
    void isFiltered_excludesIncludeDeleted() {
        assertThat(ResourceListQuery.unfiltered(true).isFiltered()).isFalse();
        assertThat(new ResourceListQuery("x", null, null, false).isFiltered()).isTrue();
        assertThat(new ResourceListQuery(null, false, null, false).isFiltered()).isTrue();
        assertThat(new ResourceListQuery(null, null, true, false).isFiltered()).isTrue();
    }

    @Test
    @DisplayName("matches — 삭제분은 삭제 포함일 때만")
    void matches_deletedOnlyWhenIncluded() {
        BoardModel deleted = board(true, false, true);

        assertThat(ResourceListQuery.unfiltered(false).matches(deleted)).isFalse();
        assertThat(ResourceListQuery.unfiltered(true).matches(deleted)).isTrue();
    }

    @Test
    @DisplayName("matches — 활성 · 사용 중단은 effective 값으로 판정(부모 때문에 꺼진 자식도 비활성)")
    void matches_usesEffectiveFlags() {
        BoardModel effectivelyOff = BoardModel.builder()
                .id(2L).vendor(Vendor.GIGABYTE).modelName("MS73")
                .ownEnabled(true).isEnabled(false)          // 운영자 의도는 켜짐 · 현재값은 꺼짐
                .ownDeprecated(false).isDeprecated(true)
                .isDeleted(false).build();

        assertThat(new ResourceListQuery(null, false, null, false).matches(effectivelyOff)).isTrue();
        assertThat(new ResourceListQuery(null, true, null, false).matches(effectivelyOff)).isFalse();
        assertThat(new ResourceListQuery(null, null, true, false).matches(effectivelyOff)).isTrue();
        assertThat(new ResourceListQuery(null, null, false, false).matches(effectivelyOff)).isFalse();
    }

    @Test
    @DisplayName("matches — 검색어는 대소문자 무시 부분 일치 · 검색 대상 중 하나라도 · null 대상은 건너뜀")
    void matches_searchCaseInsensitive_nullSafe() {
        BoardModel b = board(true, false, false);
        ResourceListQuery q = new ResourceListQuery("CE0", null, null, false);

        assertThat(q.matches(b, null, "ms04-ce0")).isTrue();
        assertThat(q.matches(b, "Gigabyte", null)).isFalse();
        assertThat(q.matches(b)).isFalse();   // 검색 대상이 없으면 검색어는 맞을 수 없다
    }
}
