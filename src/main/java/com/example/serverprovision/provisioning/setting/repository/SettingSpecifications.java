package com.example.serverprovision.provisioning.setting.repository;

import com.example.serverprovision.global.web.list.LikePattern;
import com.example.serverprovision.provisioning.setting.dto.request.SettingListQuery;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 세팅 정의서 목록의 동적 필터(S8-1). 축마다 술어 하나이고 축이 비어 있으면 그 술어가 빠진다 —
 * 축이 늘 때 메서드명 조합이 곱으로 자라는 대신 술어 하나가 더해진다({@code PurgeLogServiceImpl.buildSpec} 선례).
 */
public final class SettingSpecifications {

    private SettingSpecifications() {
    }

    public static Specification<SettingDefinition> of(SettingListQuery q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q.q() != null) {
                predicates.add(nameContains(root, cb, q.q()));
            }
            if (q.enabled() != null) {
                predicates.add(cb.equal(root.get("isEnabled"), q.enabled()));
            }
            if (q.deprecated() != null) {
                predicates.add(cb.equal(root.get("isDeprecated"), q.deprecated()));
            }
            if (!q.includeDeleted()) {
                predicates.add(cb.isFalse(root.get("isDeleted")));
            }
            // 여러 단계를 골랐으면 모두 포함해야 한다(AND) — 필터 띠의 다른 축처럼 고를수록 좁아진다.
            for (SettingProcessType type : q.processTypes()) {
                predicates.add(hasProcessType(root, query, cb, type));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate nameContains(Root<SettingDefinition> root, CriteriaBuilder cb, String q) {
        return cb.like(cb.lower(root.get("name")), LikePattern.contains(q), LikePattern.ESCAPE);
    }

    /**
     * 단계 포함 여부는 상관 서브쿼리 {@code exists} 로 묻는다. {@code SettingProcess} 에 정의서 역참조가 없고,
     * join 으로 풀면 단계 수만큼 행이 불어 {@code distinct} 와 count 보정이 따라붙기 때문이다.
     */
    private static Predicate hasProcessType(Root<SettingDefinition> root, CriteriaQuery<?> query,
                                            CriteriaBuilder cb, SettingProcessType type) {
        Subquery<Integer> exists = query.subquery(Integer.class);
        Root<SettingDefinition> correlated = exists.correlate(root);
        Join<SettingDefinition, SettingProcess> process = correlated.join("processes");
        exists.select(cb.literal(1)).where(cb.equal(process.get("processType"), type));
        return cb.exists(exists);
    }
}
