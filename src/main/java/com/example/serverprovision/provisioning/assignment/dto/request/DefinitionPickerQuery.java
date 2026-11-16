package com.example.serverprovision.provisioning.assignment.dto.request;

import com.example.serverprovision.global.web.list.vo.DateRange;
import com.example.serverprovision.global.web.list.vo.DateRangeQuery;
import com.example.serverprovision.provisioning.setting.dto.request.SettingListQuery;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 정의서 고르기 모달의 검색 조건(R20) — 서버 단건 · 그룹 일괄 두 모달이 쓴다.
 *
 * <p>이름 · 단계 · 등록 시각은 세팅 정의서 목록과 같은 조건이라 {@link #toListQuery()} 로 {@link SettingListQuery} 에 넘기고,
 * 거르는 일은 목록과 같은 {@code SettingSpecifications} 가 DB 에서 한다(파라미터 이름도 목록과 같다 — q · processType · from · to).
 * 이 record 가 직접 거르는 것은 모달에만 있는 두 축뿐이다 — 대상 보드(단계 내용에서 파생되어 컬럼이 없다)와
 * 할당 가능(서버 · 그룹마다 판정이 다르다).</p>
 *
 * @param q              이름 검색어 · 비면 전체
 * @param processTypes   반드시 가진 단계(반복 파라미터 {@code processType}, 목록과 같이 AND)
 * @param from           등록 시각 시작일 · 비면 기본(최근 1 년)
 * @param to             등록 시각 종료일 · 비면 오늘
 * @param boards         대상 보드 모델 id(반복 파라미터 {@code board}, 줄 안 OR)
 * @param boardless      대상 보드가 없는(보드 무관) 정의서도 보드 조건에 넣는가
 * @param assignableOnly 할당 가능한 것만(서버: 잠기지 않은 것 · 그룹: 붙는 멤버가 있는 것)
 */
public record DefinitionPickerQuery(
        String q,
        @BindParam("processType") Set<SettingProcessType> processTypes,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @BindParam("board") Set<Long> boards,
        Boolean boardless,
        Boolean assignableOnly
) implements DateRangeQuery {

    public DefinitionPickerQuery {
        processTypes = processTypes == null ? Set.of() : Set.copyOf(processTypes);
        boards = boards == null ? Set.of() : Set.copyOf(boards);
    }

    /** 조건 없음 — 응답 조립 테스트용. */
    public static DefinitionPickerQuery none() {
        return new DefinitionPickerQuery(null, null, null, null, null, null, null);
    }

    /** 비어 있는 기간 끝을 기본 범위로 채운 조건(S8-5 {@link DateRange} 규칙) — 조회 조건 창의 날짜 칸이 이 값을 그린다. */
    public DefinitionPickerQuery withDefaultRange(LocalDate today) {
        DateRange filled = range().withDefault(today);
        return new DefinitionPickerQuery(q, processTypes, filled.from(), filled.to(), boards, boardless, assignableOnly);
    }

    /** 목록과 같은 축(이름 · 단계 · 등록 시각)을 목록 조회 조건으로 — 삭제분 제외 · 활성 여부는 할당 판정이 따로 본다. */
    public SettingListQuery toListQuery() {
        return new SettingListQuery(q, null, null, processTypes, false, from, to, null, null);
    }

    /** 조회 조건 창의 단계 칩 — 단계 표시명의 SSOT 는 {@link SettingProcessType}. */
    public List<SettingProcessType> processOptions() {
        return List.of(SettingProcessType.values());
    }

    public boolean isBoardless() {
        return Boolean.TRUE.equals(boardless);
    }

    public boolean isAssignableOnly() {
        return Boolean.TRUE.equals(assignableOnly);
    }

    /**
     * 모달에만 있는 두 축(대상 보드 · 할당 가능)에 맞는가. 목록과 같은 축은 이미 {@link #toListQuery()} 로 걸렀다.
     *
     * @param assignable 이 문맥에서 고를 수 있는가(서버: 잠기지 않음 · 그룹: 붙는 멤버가 있음)
     */
    public boolean matchesContext(SettingSummaryResponse definition, boolean assignable) {
        return boardMatches(definition.requiredBoardModelId()) && (!isAssignableOnly() || assignable);
    }

    /** 보드 줄은 칩끼리 OR 다 — 아무 칩도 고르지 않으면 거르지 않는다. */
    private boolean boardMatches(Long boardModelId) {
        if (boards.isEmpty() && !isBoardless()) {
            return true;
        }
        return boardModelId == null ? isBoardless() : boards.contains(boardModelId);
    }
}
