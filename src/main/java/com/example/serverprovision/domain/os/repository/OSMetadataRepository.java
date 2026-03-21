package com.example.serverprovision.domain.os.repository;

import com.example.serverprovision.domain.os.entity.OSMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OSMetadataRepository extends JpaRepository<OSMetadata, Long> {
    List<OSMetadata> findAllByEnabledIsTrue();
}