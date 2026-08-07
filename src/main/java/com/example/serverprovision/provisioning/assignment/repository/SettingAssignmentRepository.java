package com.example.serverprovision.provisioning.assignment.repository;

import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingAssignmentRepository extends JpaRepository<SettingAssignment, Long> {

    /** 활성 스냅샷(supersededAt IS NULL) — 게스트당 최대 1개(활성 유일성, 결정 D-A). */
    Optional<SettingAssignment> findByGuestServer_IdAndSupersededAtIsNull(UUID guestServerId);

    /** 활성 유일성 가드 — 이미 활성 스냅샷이 있으면 재할당은 409(안전망, UI 1차 차단). */
    boolean existsByGuestServer_IdAndSupersededAtIsNull(UUID guestServerId);

    /**
     * reaper 수거 대상(U3-2-a) — 재할당으로 논리 종료됐으나 <b>한 번도 개시 안 된</b> 순수 쓰레기 행 중 TTL 경과분.
     * {@code supersededAt IS NOT NULL AND consumedAt IS NULL AND supersededAt < threshold}. 소비 이력 행(감사
     * 가치)과 활성 행은 술어에서 제외된다.
     */
    List<SettingAssignment> findBySupersededAtIsNotNullAndConsumedAtIsNullAndSupersededAtBefore(LocalDateTime threshold);

    /**
     * 정의서 역참조 카운트(U3-2-b, DEC-D) — 이 정의서를 참조하는 <b>활성</b> 할당 수. 삭제 경고 문구용.
     * {@code SourceDefinitionRef} 는 {@code @Embedded} 라 파생 쿼리가 {@code sourceDefinitionRef.definitionId}
     * 로 소프트참조 스칼라를 탐색한다({@code definition_id} 컬럼, FK 아님).
     */
    long countBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(Long definitionId);

    /** 정의서 역참조 존재 여부(저비용 분기) — 활성 할당 술어 동일. */
    boolean existsBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(Long definitionId);
}
