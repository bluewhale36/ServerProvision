package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.BoardLookupRow;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.global.web.list.dto.LookupChip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 게스트 서버 목록의 보드 검색 선택기(S8-6) — 검색 팝업의 행과 줄에 쌓인 칩 이름을 낸다.
 * 후보는 등록된(삭제되지 않은) 보드 모델 전부이고 행에 서버 수를 함께 보인다(CP4 사용자 지시 — 서버가 없는 보드도 고를 수 있다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestServerLookupService {

    private final GuestServerDetailRepository detailRepository;

    /** 검색 팝업 행 — 이름 · 제조사 부분 일치 · 비활성 보드는 includeDisabled 일 때만(사용 중단은 거르지 않음 · CP6 지시). */
    public List<BoardLookupRow> lookupBoards(String q, boolean includeDisabled) {
        return detailRepository.findBoardLookupRows().stream()
                .filter(row -> includeDisabled || row.enabled())
                .filter(row -> row.matches(q))
                .toList();
    }

    /** 고른 보드 id 중 등록된 보드만 칩으로 — 없는 id(삭제 포함)는 칩도 조건도 남기지 않는다. */
    public List<LookupChip> boardChips(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return detailRepository.findBoardLookupRows().stream()
                .filter(row -> ids.contains(row.id()))
                .map(row -> new LookupChip(String.valueOf(row.id()), row.name()))
                .toList();
    }
}
