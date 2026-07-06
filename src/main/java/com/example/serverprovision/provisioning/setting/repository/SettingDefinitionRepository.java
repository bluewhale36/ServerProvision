package com.example.serverprovision.provisioning.setting.repository;

import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettingDefinitionRepository extends JpaRepository<SettingDefinition, Long> {

    boolean existsByName(String name);

    /** PUT 명칭 중복 검사 — 자기 자신은 제외. */
    boolean existsByNameAndIdNot(String name, Long id);

    /** BIOS 세팅 템플릿 사용중 판정(U2-2-3) — @ElementCollection 은 JPQL join 으로 질의한다. */
    @Query("select count(p) from SettingProcess p join p.templateRefs ref where ref = :templateId")
    long countProcessesReferencingTemplate(@Param("templateId") Long templateId);
}
