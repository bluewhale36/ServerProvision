package com.example.serverprovision.domain.os.repository;

import com.example.serverprovision.domain.os.entity.OSEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OSEnvironmentRepository extends JpaRepository<OSEnvironment, Long> {

    // 특정 OS 메타데이터에 속한 환경 목록 조회
    List<OSEnvironment> findAllByOsMetadata_Id(Long osMetadataId);
}
