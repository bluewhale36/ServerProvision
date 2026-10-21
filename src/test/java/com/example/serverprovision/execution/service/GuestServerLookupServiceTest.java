package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.BoardLookupRow;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.global.web.list.dto.LookupChip;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GuestServerLookupServiceTest {

    @Mock GuestServerDetailRepository detailRepository;
    @InjectMocks GuestServerLookupService service;

    @BeforeEach
    void rows() {
        given(detailRepository.findBoardLookupRows()).willReturn(List.of(
                new BoardLookupRow(1L, "MD72-HB3", Vendor.GIGABYTE, 31L, true, false),
                new BoardLookupRow(2L, "MS04-CE0", Vendor.GIGABYTE, 0L, true, true),     // 사용 중단 — 늘 후보
                new BoardLookupRow(3L, "P13R-E", Vendor.ASUS, 0L, false, false)));       // 비활성 — 토글 켤 때만
    }

    @Test
    @DisplayName("S8-6 — 비활성 보드는 includeDisabled 일 때만 · 사용 중단 보드는 늘 후보 · 서버가 없는 보드(0 대)도 후보다")
    void lookupBoards_disabledToggle() {
        assertThat(service.lookupBoards(null, false)).extracting(BoardLookupRow::name).containsExactly("MD72-HB3", "MS04-CE0");
        assertThat(service.lookupBoards(null, true)).extracting(BoardLookupRow::name).containsExactly("MD72-HB3", "MS04-CE0", "P13R-E");
    }

    @Test
    @DisplayName("S8-6 — 검색어는 이름 또는 제조사 표시명에 부분 일치(대소문자 무시)")
    void lookupBoards_query() {
        assertThat(service.lookupBoards("ms0", false)).extracting(BoardLookupRow::name).containsExactly("MS04-CE0");
        assertThat(service.lookupBoards("asus", true)).extracting(BoardLookupRow::name).containsExactly("P13R-E");
        assertThat(service.lookupBoards("없음", true)).isEmpty();
    }

    @Test
    @DisplayName("S8-6 — 칩은 등록된 보드만 · 없는 id 는 빠진다")
    void boardChips_knownOnly() {
        assertThat(service.boardChips(Set.of(2L, 99L))).containsExactly(new LookupChip("2", "MS04-CE0"));
        assertThat(service.boardChips(Set.of())).isEmpty();
    }
}
