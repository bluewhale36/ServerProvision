package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.ProvisioningProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProvisioningProgressRepository extends JpaRepository<ProvisioningProgress, UUID> {

    Optional<ProvisioningProgress> findByGuestServer_Id(UUID guestServerId);

    List<ProvisioningProgress> findAllByGuestServer_IdIn(List<UUID> guestServerIds);
}
