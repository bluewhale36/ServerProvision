package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.HostNicBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HostNicBindingRepository extends JpaRepository<HostNicBinding, UUID> {

    /**
     * 목록용 — 여러 서버의 primary NIC 만 한 번에 적재.
     */
    @Query("select n from HostNicBinding n where n.guestServer.id in :serverIds and n.isPrimary = true")
    List<HostNicBinding> findPrimaryByServerIdIn(@Param("serverIds") List<UUID> serverIds);

    /**
     * 상세용 — 단일 서버의 모든 NIC. primary 우선, 이후 바인딩 시각 순.
     */
    @Query("select n from HostNicBinding n where n.guestServer.id = :serverId order by n.isPrimary desc, n.boundedAt asc")
    List<HostNicBinding> findAllByServerIdOrderByPrimary(@Param("serverId") UUID serverId);
}
