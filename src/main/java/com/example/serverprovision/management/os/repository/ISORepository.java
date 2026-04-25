package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.ISO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ISO 영속 연산. 단건 조회는 (id, osImageId) 쌍으로 수행해서 URL 변조로 다른 OS 의 ISO 를 건드리는 것을 막는다.
 * 상태(is_deleted / is_enabled) 분기는 Service 레이어에서 처리한다.
 */
public interface ISORepository extends JpaRepository<ISO, Long> {

    Optional<ISO> findByIdAndOsImage_Id(Long id, Long osImageId);

    /**
     * 동일 checksum 을 가진 활성 ISO 중 가장 먼저 발견되는 것을 돌려준다.
     * 중복 업로드 방지에서 "이미 등록된 경로" 를 사용자에게 안내하기 위해 사용한다.
     */
    Optional<ISO> findFirstByChecksumAndIsDeletedFalse(String checksum);

    /**
     * 업로드 intent 사전 검사용 — 같은 OS 내 동일 isoPath 로 등록된 활성 ISO 조회.
     * 존재하면 "같은 경로에 이미 등록됨" 으로 사용자에게 알려 업로드 자체를 스킵한다.
     */
    Optional<ISO> findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(Long osImageId, String isoPath);

    // ---- MK1 reconciliation 인벤토리 -------------------------------

    /** Markable 인벤토리 (활성). MK1 PathReconciliationService 가 OS_ISO 자원 수집 시 사용. */
    List<ISO> findAllByIsDeletedFalse();

    /** soft-deleted 자원의 ID Set (D20). ORPHAN 분류 후처리에서 매칭 제외 용도. */
    @Query("select i.id from ISO i where i.isDeleted = true")
    Set<Long> findIdsByIsDeletedTrue();
}
