package com.example.serverprovision.global.security.springsecurity.repository;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.dto.request.UserListQuery;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.web.list.DateRangePredicates;
import com.example.serverprovision.global.web.list.LikePattern;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 사용자 관리 목록의 동적 필터(MA8) — 축마다 술어 하나, 빈 축은 빠진다({@code GroupSpecifications} 와 같은 모양). */
public final class UsersSpecifications {

	private UsersSpecifications() {
	}

	public static Specification<Users> of(UserListQuery q) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (q.q() != null) {
				predicates.add(cb.like(cb.lower(root.get("name")), LikePattern.contains(q.q()), LikePattern.ESCAPE));
			}
			if (q.enabled() != null) {
				predicates.add(q.enabled() ? cb.isTrue(root.get("isEnabled")) : cb.or(cb.isFalse(root.get("isEnabled")), cb.isNull(root.get("isEnabled"))));
			}
			if (q.level() != null) {
				predicates.add(highestLevelIs(q.level(), root, query, cb));
			}
			DateRangePredicates.addWithin(predicates, cb, root.get("createdAt"), q.range());
			return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** 가장 높은 등급이 {@code level} — 그 행이 있고, 그보다 높은 등급의 행은 없다(권한 행이 펼쳐져 저장되므로). */
	private static Predicate highestLevelIs(Role level, Root<Users> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
		List<Role> higher = Arrays.stream(Role.values()).filter(r -> r.ordinal() < level.ordinal()).toList();
		Predicate hasLevel = cb.exists(roleRow(root, query, cb, List.of(level)));
		if (higher.isEmpty()) {
			return hasLevel;
		}
		return cb.and(hasLevel, cb.not(cb.exists(roleRow(root, query, cb, higher))));
	}

	private static Subquery<Long> roleRow(Root<Users> root, CriteriaQuery<?> query, CriteriaBuilder cb, List<Role> roles) {
		Subquery<Long> sub = query.subquery(Long.class);
		Root<UsersRole> r = sub.from(UsersRole.class);
		return sub.select(r.get("id")).where(cb.equal(r.get("user"), root), r.get("role").in(roles));
	}
}
