package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProvisioningProgressRepository extends JpaRepository<ProvisioningProgress, UUID> {

    Optional<ProvisioningProgress> findByGuestServer_Id(UUID guestServerId);

    List<ProvisioningProgress> findAllByGuestServer_IdIn(List<UUID> guestServerIds);

    /**
     * 집행 워커의 대상(E2-2) — 커서가 이 step 들 중 하나에 있고, 아직 실패 · 종단하지 않은 게스트.
     *
     * <p>step 집합을 인자로 받는 이유는 축이 늘어도 이 질의가 그대로이기 때문이다 — 호출자가
     * {@code FirmwareAxis} 를 순회해 집합을 만든다(D-1). 회수된 서버는 게스트 쪽 조건이라 여기서
     * 거르지 않고 워커가 판정 첫 행에서 건너뛴다(§5 1행).</p>
     */
    List<ProvisioningProgress> findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(
            Collection<ProvisioningPhaseStep> steps);
}
