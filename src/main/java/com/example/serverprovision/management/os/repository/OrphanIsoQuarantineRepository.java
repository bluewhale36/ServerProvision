package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.OrphanIsoQuarantine;
import com.example.serverprovision.management.os.enums.OrphanRecoveryState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrphanIsoQuarantineRepository extends JpaRepository<OrphanIsoQuarantine, Long> {

	Optional<OrphanIsoQuarantine> findByRecoveryId(String recoveryId);

	List<OrphanIsoQuarantine> findByStateOrderByCreatedAtDesc(OrphanRecoveryState state);

	/** TTL reaper 용 — 특정 상태 + 생성 시각 이전. */
	List<OrphanIsoQuarantine> findByStateAndCreatedAtBefore(OrphanRecoveryState state, LocalDateTime threshold);
}
