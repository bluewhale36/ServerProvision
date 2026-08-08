package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftHandling;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * MK4-1 — 처리 이력 저장소. 되돌리기 화면이 생기면 여기서 "되돌릴 수 있는 마지막 처리" 를 찾는다.
 */
public interface DriftHandlingRepository extends JpaRepository<DriftHandling, Long> {

	List<DriftHandling> findByDriftOrderByHandledAtDesc(Drift drift);

	/**
	 * 되돌리기 화면의 진입점이 될 조회. 이 슬라이스는 기록까지이므로 아직 소비처가 없다.
	 */
	Optional<DriftHandling> findFirstByDriftAndReversibleTrueOrderByHandledAtDesc(Drift drift);
}
