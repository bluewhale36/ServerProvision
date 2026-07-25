package com.example.serverprovision.global.history.repository;

import com.example.serverprovision.global.history.entity.AssetVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 파일 버전 이력 조회·정리 쿼리(E1-I-2-b-1). 파생 쿼리 이름은 {@code global.trash.PurgeLogRepository} ·
 * {@code maintenance.reconciliation.DriftReportRepository} 관례를 따른다.
 */
public interface AssetVersionRepository extends JpaRepository<AssetVersion, Long> {

    /** 한 자산의 버전을 최신 순(id 내림차순)으로. 목록·롤백 UI(b-2)가 소비. */
    List<AssetVersion> findByCategoryAndNameOrderByIdDesc(String category, String name);

    /** 한 자산의 보관 버전 수 — 보존 상한(최근 N) 초과 판정용. */
    long countByCategoryAndName(String category, String name);

    /** 한 자산의 버전을 오래된 순(id 오름차순)으로 페이징 — FIFO 정리에서 초과분 N개를 가져온다. */
    Page<AssetVersion> findByCategoryAndNameOrderByIdAsc(String category, String name, Pageable pageable);
}
