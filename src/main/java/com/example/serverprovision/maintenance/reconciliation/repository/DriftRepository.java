package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriftRepository extends JpaRepository<Drift, Long> {
	// 단건 조회 / 삭제는 JpaRepository 기본 메서드로 충분.
	// dismiss 시 PathReconciliationService 가 findById + report.removeDrift() 흐름.
}
