package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.GuestServerDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuestServerDetailRepository extends JpaRepository<GuestServerDetail, UUID> {

    /**
     * 목록용 — 여러 서버의 detail 을 boardModel 까지 fetch join 으로 한 번에 적재(N+1 회피).
     */
    @Query("select d from GuestServerDetail d join fetch d.boardModel where d.guestServer.id in :serverIds")
    List<GuestServerDetail> findAllByServerIdInWithBoardModel(@Param("serverIds") List<UUID> serverIds);

    /**
     * 상세용 — 단일 서버의 detail + boardModel.
     */
    @Query("select d from GuestServerDetail d join fetch d.boardModel where d.guestServer.id = :serverId")
    Optional<GuestServerDetail> findByServerIdWithBoardModel(@Param("serverId") UUID serverId);
}
