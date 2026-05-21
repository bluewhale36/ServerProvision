package com.example.serverprovision.global.trash.repository;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.trash.entity.PurgeLog;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * S5-2-4 — purge_log 접근.
 *
 * <p>{@link JpaSpecificationExecutor} 로 Pageable + 필터 조합을 service 단에서 동적으로 작성
 * (resource_type / origin / outcome / from / to 5종 필터). 정적 메서드 시그니처 폭증 회피 —
 * CLAUDE.md "조건 분기문 무분별 확장 절대 지양" 준수.</p>
 *
 * <p>일반 조회는 method-name 규약, 그룹 쿼리는 JPQL.</p>
 */
public interface PurgeLogRepository extends JpaRepository<PurgeLog, Long>, JpaSpecificationExecutor<PurgeLog> {

	/**
	 * 자원별 회고 — idx_purge_log_resource 활용.
	 */
	List<PurgeLog> findByResourceTypeAndResourceIdOrderByOccurredAtAsc(ResourceType resourceType, Long resourceId);

	/**
	 * 자원별 누적 FAILED count — attemptNumber 계산용. PurgeExecutor 가 본 메서드 호출.
	 */
	long countByResourceTypeAndResourceIdAndOutcome(ResourceType resourceType, Long resourceId, PurgeOutcome outcome);

	/**
	 * 자원별 마지막 outcome=FAILED 자원 셋 — 휴지통 list 의 "재시도 대기" 배지 lookup.
	 *
	 * <p>자원 단위로 가장 최근 row 의 outcome 이 FAILED 인 자원만 반환. JPQL window function 대신
	 * subquery 패턴 — MariaDB / H2 호환 (window function 은 MariaDB 10.2+ 만).</p>
	 */
	@Query(
			"""
					select new com.example.serverprovision.global.trash.ResourceKey(p.resourceType, p.resourceId)
					from PurgeLog p
					where p.outcome = com.example.serverprovision.global.trash.enums.PurgeOutcome.FAILED
					  and p.occurredAt = (
					      select max(p2.occurredAt) from PurgeLog p2
					      where p2.resourceType = p.resourceType
					        and p2.resourceId = p.resourceId)
					"""
	)
	List<ResourceKey> findResourceKeysWithLastOutcomeFailed();

	/**
	 * Pageable + Specification 조합 — service 측에서 Specification 합성 후 호출.
	 * (JpaSpecificationExecutor 의 {@code findAll(Specification, Pageable)} 사용)
	 */
	@Override
	Page<PurgeLog> findAll(org.springframework.data.jpa.domain.Specification<PurgeLog> spec, Pageable pageable);
}
