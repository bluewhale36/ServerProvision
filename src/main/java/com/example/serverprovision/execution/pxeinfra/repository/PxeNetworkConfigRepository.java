package com.example.serverprovision.execution.pxeinfra.repository;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * PXE 네트워크 구성 저장소. 이 테이블은 고정 싱글턴(최대 1행)이라 {@link #findFirstByOrderByIdAsc} 로 그
 * 유일한 행을 읽는다 — 존재하지 않으면 아직 최초 저장 전이다.
 */
public interface PxeNetworkConfigRepository extends JpaRepository<PxeNetworkConfig, Long> {

    Optional<PxeNetworkConfig> findFirstByOrderByIdAsc();
}
