package com.example.serverprovision.provisioning.setting.repository;

import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SettingDefinitionRepository extends JpaRepository<SettingDefinition, Long> {

    /** create 명칭 중복 검사 — <b>활성</b> 정의서만 대상(U3-2-b DEC-B, soft-delete 이름은 재사용 허용). */
    boolean existsByNameAndIsDeletedFalse(String name);

    /** update 명칭 중복 검사 — 자기 자신 제외 + 활성 전용(DEC-B). */
    boolean existsByNameAndIdNotAndIsDeletedFalse(String name, Long id);

    /** 수정 대상 조회 — soft-deleted 정의서는 편집 불가라 활성만 찾는다(없으면 404). */
    Optional<SettingDefinition> findByIdAndIsDeletedFalse(Long id);

    /** purge 대상 조회 — soft-delete 선행 강제(DEC-E). 활성/부재는 빈 Optional → 404. */
    Optional<SettingDefinition> findByIdAndIsDeletedTrue(Long id);

    /** 목록 기본 — 활성 전용(DEC-F). includeDeleted 는 상속 {@code findAll(Sort)} 로 전건 조회. */
    List<SettingDefinition> findAllByIsDeletedFalseOrderByIdAsc();

    /** BIOS 세팅 템플릿 사용중 판정(U2-2-3) — @ElementCollection 은 JPQL join 으로 질의한다. */
    @Query("select count(p) from SettingProcess p join p.templateRefs ref where ref = :templateId")
    long countProcessesReferencingTemplate(@Param("templateId") Long templateId);
}
