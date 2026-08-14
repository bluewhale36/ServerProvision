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

	/**
	 * MK4-4-3 — 이 드리프트를 <b>마지막으로 정밀 점검이 본 시각</b>.
	 *
	 * <p>이력 조회로 대신하지 않는 이유가 둘이다. 이력은 자리 관계로 <b>잘려 있고</b>(감춘 건수를
	 * 따로 밝힌다), 깊이를 사람이 읽는 문자열 라벨로만 들고 있어 화면이 그것을 되파싱하면 문구가
	 * 바뀌는 순간 조용히 어긋난다. 사실은 사실대로 묻는다.</p>
	 */
	@Query("select max(o.observedAt) from DriftObservation o"
			+ " where o.drift = :drift and o.report.deep = true")
	java.util.Optional<java.time.Instant> findLastDeepObservedAt(@Param("drift") Drift drift);
}
