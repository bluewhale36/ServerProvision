package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriftReportRepository extends JpaRepository<DriftReport, Long> {

	/**
	 * 가장 최근 1 건. {@code findFirstBy...OrderBy} 로 LIMIT 1.
	 */
	Optional<DriftReport> findFirstByOrderByScannedAtDesc();

	/**
	 * 페이지네이션 이력. Pageable 의 sort 가 scannedAt DESC 인 게 일반적.
	 */
	Page<DriftReport> findAllBy(Pageable pageable);

	/**
	 * FIFO prune 용 — 특정 보관 한도를 넘는 오래된 보고서 N 건 삭제.
	 * 호출 측에서 {@code count() - retentionCount} 만큼 삭제 — 본 인터페이스는 가장 오래된 N 건 조회만 제공.
	 */
	Page<DriftReport> findAllByOrderByScannedAtAsc(Pageable pageable);
}
