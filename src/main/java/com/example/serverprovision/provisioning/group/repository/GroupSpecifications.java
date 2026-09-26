package com.example.serverprovision.provisioning.group.repository;

import com.example.serverprovision.global.web.list.LikePattern;
import com.example.serverprovision.provisioning.group.dto.request.GroupListQuery;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** 서버 그룹 목록의 동적 필터(S8-1) — 축마다 술어 하나, 빈 축은 빠진다. */
public final class GroupSpecifications {

    private GroupSpecifications() {
    }

    public static Specification<GuestServerGroup> of(GroupListQuery q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q.q() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), LikePattern.contains(q.q()), LikePattern.ESCAPE));
            }
            if (q.hasStandardDefinition() != null) {
                predicates.add(q.hasStandardDefinition()
                        ? cb.isNotNull(root.get("standardDefinitionId"))
                        : cb.isNull(root.get("standardDefinitionId")));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
