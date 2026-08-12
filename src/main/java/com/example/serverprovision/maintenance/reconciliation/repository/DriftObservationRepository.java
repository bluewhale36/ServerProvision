package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * MK4-1 — 관측 저장소. 한 문제가 어느 회차들에서 보였는지를 되짚는 용도다.
 */
public interface DriftObservationRepository extends JpaRepository<DriftObservation, Long> {

	List<DriftObservation> findByDriftOrderByObservedAtDesc(Drift drift);

	/**
	 * MK4-4-2 — 이력 화면이 쓴다. 회차를 함께 읽는 이유는 화면이 관측마다 "어느 점검에서 보였는가"
	 * 를 밝히기 때문이다 — 지연 로딩으로 두면 관측 수만큼 조회가 늘고, 오래 남은 드리프트는 그
	 * 수가 수십 · 수백에 이른다.
	 */
	@Query("select o from DriftObservation o join fetch o.report"
			+ " where o.drift = :drift order by o.observedAt desc")
	List<DriftObservation> findByDriftWithReportOrderByObservedAtDesc(@Param("drift") Drift drift);
}
