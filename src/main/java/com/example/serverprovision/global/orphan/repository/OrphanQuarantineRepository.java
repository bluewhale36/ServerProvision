package com.example.serverprovision.global.orphan.repository;

import com.example.serverprovision.global.orphan.entity.OrphanQuarantine;
import com.example.serverprovision.global.orphan.enums.OrphanRecoveryState;
import com.example.serverprovision.global.orphan.exception.OrphanRecoveryNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrphanQuarantineRepository extends JpaRepository<OrphanQuarantine, Long> {

	Optional<OrphanQuarantine> findByRecoveryId(String recoveryId);

	List<OrphanQuarantine> findByStateOrderByCreatedAtDesc(OrphanRecoveryState state);

	/** TTL reaper 용 — 특정 상태 + 생성 시각 이전. */
	List<OrphanQuarantine> findByStateAndCreatedAtBefore(OrphanRecoveryState state, LocalDateTime threshold);

	/** R9-4 — 무결성 점검 페이지의 격리 대기 안내 배너용 count. */
	long countByState(OrphanRecoveryState state);

	/**
	 * recoveryId 로 조회하거나 {@link OrphanRecoveryNotFoundException}(404). 격리 조회/복구 액션의 SSOT lookup —
	 * QuarantineService(get) 와 RecoveryService(retry/discard) 가 공유한다(중복 회피).
	 */
	default OrphanQuarantine getByRecoveryIdOrThrow(String recoveryId) {
		return findByRecoveryId(recoveryId)
				.orElseThrow(() -> new OrphanRecoveryNotFoundException(recoveryId));
	}
}
