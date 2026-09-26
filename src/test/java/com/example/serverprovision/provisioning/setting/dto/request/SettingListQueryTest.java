package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.enums.SettingSortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/** 조회 조건의 기본값 정규화(S8-1) — 파라미터가 없으면 서버가 스스로 기본값을 채운다. */
class SettingListQueryTest {

    @Test
    @DisplayName("아무 조건도 없으면 번호 오름차순 · 삭제 제외 · 필터 없음")
    void defaults() {
        SettingListQuery q = new SettingListQuery(null, null, null, null, null, null, null);

        assertThat(q.sort()).isEqualTo(SettingSortField.ID);
        assertThat(q.dir()).isEqualTo(Sort.Direction.ASC);
        assertThat(q.includeDeleted()).isFalse();
        assertThat(q.processTypes()).isEmpty();
        assertThat(q.isFiltered()).isFalse();
    }

    @Test
    @DisplayName("공백 검색어는 조건이 아니다 · 앞뒤 공백은 걷는다")
    void blankQueryIsNull() {
        assertThat(new SettingListQuery("   ", null, null, null, null, null, null).q()).isNull();
        assertThat(new SettingListQuery("  표준 ", null, null, null, null, null, null).q()).isEqualTo("표준");
    }

    @Test
    @DisplayName("단계 집합의 null 원소(빈 값 파라미터)는 걸러진다")
    void nullProcessTypeElementsAreDropped() {
        SettingListQuery q = new SettingListQuery(null, null, null,
                new HashSet<>(Arrays.asList(null, SettingProcessType.RAID_CONFIGURATION)), null, null, null);

        assertThat(q.processTypes()).containsExactly(SettingProcessType.RAID_CONFIGURATION);
        assertThat(q.isFiltered()).isTrue();
    }

    @Test
    @DisplayName("방향이 없으면 정렬 항목의 기본 방향 — 생성일시는 최신순")
    void directionFallsBackToFieldDefault() {
        SettingListQuery q = new SettingListQuery(null, null, null, null, null, SettingSortField.CREATED_AT, null);

        assertThat(q.dir()).isEqualTo(Sort.Direction.DESC);
        assertThat(q.isFiltered()).isFalse();
    }

    @Test
    @DisplayName("정렬 항목 ↔ 엔티티 속성 · 기본 방향 전수")
    void sortFieldMapping() {
        assertThat(Arrays.stream(SettingSortField.values()).map(f -> f.property() + ":" + f.defaultDirection()))
                .containsExactly("id:ASC", "name:ASC", "createdAt:DESC");
    }
}
