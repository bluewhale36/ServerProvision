package com.example.serverprovision.global.trash.service.internal;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.trash.dto.response.PurgeLogResponse;
import com.example.serverprovision.global.trash.entity.PurgeLog;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;
import com.example.serverprovision.global.trash.repository.PurgeLogRepository;
import com.example.serverprovision.global.trash.service.PurgeLogService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * S5-2-4 CP4 — PurgeLogService 본체.
 *
 * <p>Specification 패턴으로 동적 필터 합성 — repository 의 메서드 시그니처 폭증 회피.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurgeLogServiceImpl implements PurgeLogService {

	private final PurgeLogRepository repository;

	@Override
	public List<PurgeLogResponse> findByResource(ResourceType resourceType, Long resourceId) {
		return repository.findByResourceTypeAndResourceIdOrderByOccurredAtAsc(resourceType, resourceId)
				.stream().map(this::toResponse).toList();
	}

	@Override
	public Page<PurgeLogResponse> findPage(
			ResourceType filterType,
			PurgeOrigin filterOrigin,
			PurgeOutcome filterOutcome,
			Instant from,
			Instant to,
			Pageable pageable
	) {
		Specification<PurgeLog> spec = buildSpec(filterType, filterOrigin, filterOutcome, from, to);
		return repository.findAll(spec, pageable).map(this::toResponse);
	}

	@Override
	public long countFailedForResource(ResourceType resourceType, Long resourceId) {
		return repository.countByResourceTypeAndResourceIdAndOutcome(resourceType, resourceId, PurgeOutcome.FAILED);
	}

	@Override
	public Set<ResourceKey> findResourcesWithLastOutcomeFailed() {
		return new HashSet<>(repository.findResourceKeysWithLastOutcomeFailed());
	}

	/**
	 * Specification 합성 — null 필터는 무시. CLAUDE.md 의 분기 응집 원칙 준수 (each predicate is independent).
	 */
	private static Specification<PurgeLog> buildSpec(
			ResourceType type,
			PurgeOrigin origin,
			PurgeOutcome outcome,
			Instant from,
			Instant to
	) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (type != null) predicates.add(cb.equal(root.get("resourceType"), type));
			if (origin != null) predicates.add(cb.equal(root.get("origin"), origin));
			if (outcome != null) predicates.add(cb.equal(root.get("outcome"), outcome));
			if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
			if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
			return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private PurgeLogResponse toResponse(PurgeLog log) {
		return new PurgeLogResponse(
				log.getId(),
				log.getResourceType(),
				log.getResourceId(),
				log.getDisplayName(),
				log.getOrigin(),
				log.getOutcome(),
				log.getOccurredAt(),
				log.getPurgedAt(),
				log.getDetails()
		);
	}
}
