package com.example.serverprovision.provisioning.group.repository;

import com.example.serverprovision.provisioning.group.entity.GuestServerGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestServerGroupMemberRepository extends JpaRepository<GuestServerGroupMember, Long> {

    /** 서버 한 대의 현재 소속. UNIQUE 제약이 최대 1행을 보장하므로 Optional 이다(DEC-B). */
    @Query("""
            select m from GuestServerGroupMember m
            join fetch m.group
            where m.guestServer.id = :serverId
            """)
    Optional<GuestServerGroupMember> findByServerId(UUID serverId);

    /**
     * 여러 서버의 현재 소속을 한 번에 — 목록 화면의 그룹 배지와 생성 폼의 후보 판정이 쓴다.
     * 서버마다 조회하면 입고 단위(수십~수백)만큼 왕복이 생긴다.
     */
    @Query("""
            select m from GuestServerGroupMember m
            join fetch m.group
            where m.guestServer.id in :serverIds
            """)
    List<GuestServerGroupMember> findAllByServerIdIn(Collection<UUID> serverIds);

    /** 어느 그룹에도 속하지 않은 서버 골라내기의 재료 — 소속이 있는 서버 id 전부. */
    @Query("select m.guestServer.id from GuestServerGroupMember m")
    List<UUID> findAllGroupedServerIds();

    /**
     * 모든 소속 — 그룹 목록의 구성 혼재 판정에 쓴다.
     * 그룹마다 멤버를 따로 읽으면 그룹 수만큼 왕복이 생기므로 한 번에 읽어 애플리케이션에서 가른다.
     */
    @Query("select m from GuestServerGroupMember m join fetch m.group")
    List<GuestServerGroupMember> findAllWithGroup();
}
