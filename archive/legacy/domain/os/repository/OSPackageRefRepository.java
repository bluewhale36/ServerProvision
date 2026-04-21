package com.example.serverprovision.domain.os.repository;

import com.example.serverprovision.domain.os.entity.OSPackageRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface OSPackageRefRepository extends JpaRepository<OSPackageRef, Long> {

    /** 주어진 OSMetadata 에 해당 이름의 패키지 참조가 존재하는지 확인. */
    boolean existsByOsMetadata_IdAndName(Long osMetadataId, String name);

    /** 인덱싱 여부 판정 — 0 이면 아직 인덱싱 안 된 상태로 해석해 검증을 건너뛴다. */
    long countByOsMetadata_Id(Long osMetadataId);

    /** 재인덱싱 시 기존 엔트리 일괄 삭제. */
    @Modifying
    @Query("delete from OSPackageRef p where p.osMetadata.id = :osMetadataId")
    int deleteAllByOsMetadataId(@Param("osMetadataId") Long osMetadataId);

    /**
     * 주어진 후보 이름 집합 중 이 OSMetadata 에 존재하는 패키지 이름들만 반환한다.
     * 검증 로직에서 "존재하는 이름"을 얻어 {@code 입력 - 존재} 차집합으로 미존재 이름을 계산하는 용도.
     */
    @Query("select p.name from OSPackageRef p " +
            "where p.osMetadata.id = :osMetadataId and p.name in :names")
    Set<String> findExistingNames(@Param("osMetadataId") Long osMetadataId,
                                  @Param("names") Collection<String> names);
}
