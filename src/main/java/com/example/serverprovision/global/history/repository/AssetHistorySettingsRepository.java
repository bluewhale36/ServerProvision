package com.example.serverprovision.global.history.repository;

import com.example.serverprovision.global.history.entity.AssetHistorySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 버전 이력 설정 저장소(E1-I-2-b-2). 단일행이라 {@code findFirstByOrderByIdAsc} 로 첫 행만 신뢰한다
 * ({@code TrashSettingsRepository} 관례).
 */
public interface AssetHistorySettingsRepository extends JpaRepository<AssetHistorySettings, Long> {

    Optional<AssetHistorySettings> findFirstByOrderByIdAsc();
}
