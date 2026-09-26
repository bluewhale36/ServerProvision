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

    /** 한 그룹의 멤버 서버 id(S8-2) — 서버 목록의 소속 그룹 조건을 서버 id 범위로 번역할 때 쓴다. */
    @Query("select m.guestServer.id from GuestServerGroupMember m where m.group.id = :groupId")
    List<UUID> findServerIdsByGroupId(Long groupId);

    /** 어느 그룹에도 속하지 않은 서버 골라내기의 재료 — 소속이 있는 서버 id 전부. */
    @Query("select m.guestServer.id from GuestServerGroupMember m")
    List<UUID> findAllGroupedServerIds();

    /**
     * 주어진 그룹들의 소속 — 그룹 목록 한 쪽의 구성 혼재 판정에 쓴다(S8-1).
     * 종전에는 전 소속을 읽었지만, 쪽 단위로 보이는 그룹만 읽으면 조회 범위가 그 쪽 안에서 닫힌다.
     */
    @Query("""
            select m from GuestServerGroupMember m
            join fetch m.group
            where m.group.id in :groupIds
            """)
    List<GuestServerGroupMember> findAllByGroupIdIn(Collection<Long> groupIds);
}
