package com.example.serverprovision.domain.node.repository;

import com.example.serverprovision.domain.node.entity.ServerNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServerNodeRepository extends JpaRepository<ServerNode, Long> {

    @Query("SELECT sn FROM ServerNode sn WHERE sn.macAddress = :macAddress AND sn.status NOT IN ('COMPLETED', 'FAILED')")
    Optional<ServerNode> findAvailableNodeByMacAddress(String macAddress);
}
