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

    /**
     * 수집 적재 전 보드 시리얼 중복 사전 검사(E1-2) — board_serial UNIQUE 를 커밋 시점 500 으로
     * 맞지 않기 위한 관용 경로의 입력. 중복이면 시리얼만 적재 생략(원문은 원장 statusMeta 보존)하고
     * 나머지 인벤토리는 정상 적재한다 — T1 하네스 실측 결함(2026-07-19) 대응.
     */
    boolean existsByBoardSerialAndGuestServer_IdNot(String boardSerial, UUID serverId);
}
