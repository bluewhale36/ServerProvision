package com.example.serverprovision.provisioning.biossetting.repository;

import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiosSettingTemplateRepository extends JpaRepository<BiosSettingTemplate, Long> {

    boolean existsByName(String name);

    /** PUT 명칭 중복 검사 — 자기 자신은 제외. */
    boolean existsByNameAndIdNot(String name, Long id);
}
