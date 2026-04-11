package com.example.serverprovision.domain.os.repository;

import com.example.serverprovision.domain.os.entity.OSPackageGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OSPackageGroupRepository extends JpaRepository<OSPackageGroup, Long> {
}
