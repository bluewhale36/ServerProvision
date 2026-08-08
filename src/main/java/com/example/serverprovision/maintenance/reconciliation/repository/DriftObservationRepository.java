package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * MK4-1 — 관측 저장소. 한 문제가 어느 회차들에서 보였는지를 되짚는 용도다.
 */
public interface DriftObservationRepository extends JpaRepository<DriftObservation, Long> {

	List<DriftObservation> findByDriftOrderByObservedAtDesc(Drift drift);
}
