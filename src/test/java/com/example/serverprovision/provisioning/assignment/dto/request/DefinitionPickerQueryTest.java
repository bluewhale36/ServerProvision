package com.example.serverprovision.provisioning.assignment.dto.request;

import com.example.serverprovision.provisioning.setting.dto.request.SettingListQuery;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R20 — 정의서 고르기 모달의 검색 조건. 이름 · 단계 · 등록 시각은 목록 조회 조건({@link SettingListQuery})으로 넘기고
 * (거르기는 SettingSpecifications · SettingSpecificationsJpaTest 소관), 이 record 는 대상 보드 · 할당 가능만 직접 거른다.
 */
class DefinitionPickerQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static SettingSummaryResponse definition(Long boardId) {
        return new SettingSummaryResponse(1L, "def", List.of(SettingProcessType.BASIC_UPDATE), false, true, false,
                LocalDateTime.of(2026, 9, 1, 9, 0), boardId, boardId == null ? null : "MS74-HB0");
    }

    @Test
    @DisplayName("목록과 같은 축(이름 · 단계 · 등록 시각)을 SettingListQuery 로 넘긴다 — 삭제분 제외 · 활성 여부는 묻지 않음")
    void toListQueryCarriesSharedAxes() {
        DefinitionPickerQuery query = new DefinitionPickerQuery("raid", Set.of(SettingProcessType.RAID_CONFIGURATION),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), Set.of(7L), true, true);

        SettingListQuery list = query.toListQuery();

        assertThat(list.q()).isEqualTo("raid");
        assertThat(list.processTypes()).containsExactly(SettingProcessType.RAID_CONFIGURATION);
        assertThat(list.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(list.to()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(list.includeDeleted()).isFalse();
        assertThat(list.enabled()).isNull();
    }

    @Test
    @DisplayName("기간이 비면 최근 1 년(DateRange.DEFAULT_SPAN)으로 채워 목록 조건에도 그대로 실린다")
    void emptyRangeDefaultsToLastYear() {
        DefinitionPickerQuery filled = DefinitionPickerQuery.none().withDefaultRange(TODAY);

        assertThat(filled.from()).isEqualTo(LocalDate.of(2025, 11, 16));
        assertThat(filled.to()).isEqualTo(TODAY);
        assertThat(filled.toListQuery().from()).isEqualTo(LocalDate.of(2025, 11, 16));
    }

    @Test
    @DisplayName("대상 보드 — 칩이 없으면 거르지 않고, 칩끼리는 OR · 보드 무관은 따로 고른다")
    void boardAxis() {
        assertThat(DefinitionPickerQuery.none().matchesContext(definition(7L), true)).isTrue();
        assertThat(DefinitionPickerQuery.none().matchesContext(definition(null), true)).isTrue();

        DefinitionPickerQuery boards = new DefinitionPickerQuery(null, null, null, null, Set.of(7L, 9L), null, null);
        assertThat(boards.matchesContext(definition(7L), true)).isTrue();
        assertThat(boards.matchesContext(definition(8L), true)).isFalse();
        assertThat(boards.matchesContext(definition(null), true)).isFalse();

        DefinitionPickerQuery boardless = new DefinitionPickerQuery(null, null, null, null, null, true, null);
        assertThat(boardless.matchesContext(definition(null), true)).isTrue();
        assertThat(boardless.matchesContext(definition(7L), true)).isFalse();
    }

    @Test
    @DisplayName("할당 가능한 것만 — 켜면 이 문맥에서 고를 수 없는 정의서(잠김 · 붙는 멤버 0)가 빠진다")
    void assignableOnlyAxis() {
        DefinitionPickerQuery onlyAssignable = new DefinitionPickerQuery(null, null, null, null, null, null, true);
        assertThat(onlyAssignable.matchesContext(definition(7L), true)).isTrue();
        assertThat(onlyAssignable.matchesContext(definition(7L), false)).isFalse();
        assertThat(DefinitionPickerQuery.none().matchesContext(definition(7L), false)).isTrue();
    }
}
