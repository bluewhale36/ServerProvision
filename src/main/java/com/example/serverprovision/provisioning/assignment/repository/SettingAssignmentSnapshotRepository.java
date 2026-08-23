package com.example.serverprovision.provisioning.assignment.repository;

import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingAssignmentSnapshotRepository extends JpaRepository<SettingAssignmentSnapshot, Long> {

    /** 활성 스냅샷(supersededAt IS NULL) — 게스트당 최대 1개(활성 유일성, 결정 D-A). */
    Optional<SettingAssignmentSnapshot> findByGuestServer_IdAndSupersededAtIsNull(UUID guestServerId);

    /** 활성 유일성 가드 — 이미 활성 스냅샷이 있으면 재할당은 409(안전망, UI 1차 차단). */
    boolean existsByGuestServer_IdAndSupersededAtIsNull(UUID guestServerId);

    /**
     * 여러 서버의 활성 스냅샷을 한 번에 (U3-5-c) — 그룹 일괄 할당의 미리보기가 "이미 할당됨" 을 가릴 때 쓴다.
     *
     * <p>멤버마다 {@link #findByGuestServer_IdAndSupersededAtIsNull} 를 부르지 않는 이유는 그 편이 멤버
     * 수만큼 질의를 내기 때문이다. 미리보기는 정의서 × 멤버 조합마다 판정하므로, 판정에 드는 재료는
     * <b>조합에 들어가기 전에 한 번씩만</b> 읽어야 한다.</p>
     */
    List<SettingAssignmentSnapshot> findByGuestServer_IdInAndSupersededAtIsNull(List<UUID> guestServerIds);

    /**
     * reaper 수거 대상(U3-2-a) — 재할당으로 논리 종료됐으나 <b>한 번도 개시 안 된</b> 순수 쓰레기 행 중 TTL 경과분.
     * {@code supersededAt IS NOT NULL AND consumedAt IS NULL AND supersededAt < threshold}. 소비 이력 행(감사
     * 가치)과 활성 행은 술어에서 제외된다.
     */
    List<SettingAssignmentSnapshot> findBySupersededAtIsNotNullAndConsumedAtIsNullAndSupersededAtBefore(LocalDateTime threshold);

    /**
     * 정의서 역참조 카운트(U3-2-b, DEC-D) — 이 정의서를 참조하는 <b>활성</b> 할당 수. 삭제 경고 문구용.
     * {@code SourceDefinitionRef} 는 {@code @Embedded} 라 파생 쿼리가 {@code sourceDefinitionRef.definitionId}
     * 로 소프트참조 스칼라를 탐색한다({@code definition_id} 컬럼, FK 아님).
     */
    long countBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(Long definitionId);

    /** 정의서 역참조 존재 여부(저비용 분기) — 활성 할당 술어 동일. */
    boolean existsBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(Long definitionId);

    /**
     * MK4-2 — 서버에 걸려 있는 모든 활성 스냅샷. 자원 사용 깊이를 계산할 때 전수 순회한다.
     *
     * <p>{@code processes} 를 함께 가져온다. 스냅샷마다 단계를 다시 조회하면 할당 수만큼 질의가
     * 반복되기 때문이다({@code @OneToMany} 지연 로딩의 N+1). {@code distinct} 는 조인으로 부풀어난
     * 부모 중복을 제거한다.</p>
     */
    @Query("SELECT DISTINCT a FROM SettingAssignmentSnapshot a LEFT JOIN FETCH a.processes WHERE a.supersededAt IS NULL")
    List<SettingAssignmentSnapshot> findBySupersededAtIsNull();
}
