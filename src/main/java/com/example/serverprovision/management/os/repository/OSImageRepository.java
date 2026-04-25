package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * OSImage 영속 연산. 모든 목록 메서드는 {@code @EntityGraph} 로 자식 ISO 를 같이 로드해 Miller 상세 렌더의 N+1 을 막는다.
 */
public interface OSImageRepository extends JpaRepository<OSImage, Long> {

    /** 기본 보기 : soft 삭제된 레코드 제외. OSName 이름 오름차순 → 등록일 내림차순(최신 버전이 상단). */
    @EntityGraph(attributePaths = "isos")
    List<OSImage> findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc();

    /** 휴지통 포함 보기 : 모든 레코드. */
    @EntityGraph(attributePaths = "isos")
    List<OSImage> findAllByOrderByOsNameAscCreatedAtDesc();

    /** 단건 조회 (삭제된 레코드 제외). 수정/토글/삭제 시 사용. */
    Optional<OSImage> findByIdAndIsDeletedFalse(Long id);

    /** 복구 조회 (삭제된 레코드만 대상). */
    Optional<OSImage> findByIdAndIsDeletedTrue(Long id);

    /** 중복 등록 방지 — 삭제된 동일 조합은 중복으로 취급하지 않는다. */
    boolean existsByOsNameAndOsVersionAndIsDeletedFalse(OSName osName, String osVersion);
}
