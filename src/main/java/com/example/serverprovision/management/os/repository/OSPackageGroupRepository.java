package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.OSPackageGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * OS 이미지 범위의 패키지 그룹 영속 연산.
 * 그룹 카드 렌더는 단순 필드만 사용하므로 별도 {@code @EntityGraph} 는 두지 않는다.
 */
public interface OSPackageGroupRepository extends JpaRepository<OSPackageGroup, Long> {

    List<OSPackageGroup> findAllByOsImage_IdOrderByGroupCode_ValueAsc(Long osImageId);

    /** 추출 upsert 조회 — 동일 (osImageId, groupCode) 가 있으면 그 엔티티를 반환. */
    Optional<OSPackageGroup> findByOsImage_IdAndGroupCode_Value(Long osImageId, String groupCode);
}
