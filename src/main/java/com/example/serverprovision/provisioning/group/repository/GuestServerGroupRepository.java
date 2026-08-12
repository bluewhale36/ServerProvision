package com.example.serverprovision.provisioning.group.repository;

import com.example.serverprovision.provisioning.group.dto.response.GroupSummaryResponse;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GuestServerGroupRepository extends JpaRepository<GuestServerGroup, Long> {

    /**
     * 이름 중복 확인 (DEC-F). 생성 폼의 사전 검사와 서비스 가드가 <b>같은 메서드</b>를 부른다 —
     * 두 곳이 각자 조건을 만들면 어긋난다.
     */
    boolean existsByName(String name);

    /** 이름 변경 시 자기 자신은 충돌 대상이 아니다. */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * 그룹 목록 한 줄씩 — 멤버 수는 집계로 센다.
     * 엔티티를 읽고 {@code memberCount()} 를 부르면 그룹 수만큼 컬렉션이 지연 로드된다(N+1).
     */
    @Query("""
            select new com.example.serverprovision.provisioning.group.dto.response.GroupSummaryResponse(
                       g.id, g.name, count(m), g.createdAt)
            from GuestServerGroup g
            left join g.members m
            group by g.id, g.name, g.createdAt
            order by g.createdAt desc
            """)
    List<GroupSummaryResponse> findAllSummaries();

    /** 상세 화면 — 멤버와 그 서버까지 한 번에 끌어온다(목록 N+1 회피). */
    @Query("""
            select distinct g from GuestServerGroup g
            left join fetch g.members m
            left join fetch m.guestServer
            where g.id = :id
            """)
    Optional<GuestServerGroup> findByIdWithMembers(Long id);
}
