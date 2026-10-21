package com.example.serverprovision.global.web.list;

import com.example.serverprovision.global.web.list.vo.DateRange;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기간 조건의 Specification 술어(S8-5) — 세 목록(세팅 정의서 · 서버 그룹 · 게스트 서버)이 같은 경계 규칙
 * (시작일 0 시 이상 · 종료일 다음 날 0 시 미만)을 쓴다. 비어 있는 끝은 술어를 더하지 않는다.
 */
public final class DateRangePredicates {

    private DateRangePredicates() {
    }

    public static void addWithin(List<Predicate> predicates, CriteriaBuilder cb, Path<LocalDateTime> at, DateRange range) {
        if (range.startInclusive() != null) {
            predicates.add(cb.greaterThanOrEqualTo(at, range.startInclusive()));
        }
        if (range.endExclusive() != null) {
            predicates.add(cb.lessThan(at, range.endExclusive()));
        }
    }
}
