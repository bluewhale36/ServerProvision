package com.example.serverprovision.provisioning.group.repository;

import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GuestServerGroupRepository extends JpaRepository<GuestServerGroup, Long>,
        JpaSpecificationExecutor<GuestServerGroup> {

    /**
     * 이름 중복 확인 (DEC-F). 생성 폼의 사전 검사와 서비스 가드가 <b>같은 메서드</b>를 부른다 —
     * 두 곳이 각자 조건을 만들면 어긋난다.
     */
    boolean existsByName(String name);

    /** 이름 변경 시 자기 자신은 충돌 대상이 아니다. */
    boolean existsByNameAndIdNot(String name, Long id);

    /** 상세 화면 — 멤버와 그 서버까지 한 번에 끌어온다(목록 N+1 회피). */
    @Query("""
            select distinct g from GuestServerGroup g
            left join fetch g.members m
            left join fetch m.guestServer
            where g.id = :id
            """)
    Optional<GuestServerGroup> findByIdWithMembers(Long id);
}
