package com.example.serverprovision.management.board.repository;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * BoardModel 영속 연산.
 * A3/A4/A5 가 추가된 뒤 호환 목록 Count/Fetch 가 필요해지면 여기에 @EntityGraph 또는 JPQL 을 덧붙인다.
 */
public interface BoardModelRepository extends JpaRepository<BoardModel, Long> {

    /** 기본 보기 : soft 삭제된 레코드 제외. Vendor 오름차순 → 등록일 내림차순(최신 등록이 상단). */
    List<BoardModel> findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

    /** 휴지통 포함 보기 : 모든 레코드. */
    List<BoardModel> findAllByOrderByVendorAscCreatedAtDesc();

    /** 단건 조회 (삭제된 레코드 제외). 수정/토글/삭제 시 사용. */
    Optional<BoardModel> findByIdAndIsDeletedFalse(Long id);

    /** 복구 조회 (삭제된 레코드만 대상). */
    Optional<BoardModel> findByIdAndIsDeletedTrue(Long id);

    /**
     * 중복 등록 방지 — 활성 상태(is_deleted=false) 에서 (vendor, modelName) 조합 유일성 검사.
     * DB 레이어의 {@code uk_vendor_model_name (vendor, model_name)} 과 이중 가드를 이룬다.
     * 삭제된 동일 조합을 재등록하려 하면 DB 유니크 제약으로 실패하므로 복구 경로를 사용해야 한다.
     */
    boolean existsByVendorAndModelNameAndIsDeletedFalse(Vendor vendor, String modelName);
}
