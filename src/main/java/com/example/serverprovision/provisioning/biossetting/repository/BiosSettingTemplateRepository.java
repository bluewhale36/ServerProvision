package com.example.serverprovision.provisioning.biossetting.repository;

import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiosSettingTemplateRepository extends JpaRepository<BiosSettingTemplate, Long> {

    /** 메인보드 영구 삭제 판정(HF26) — 그 보드의 BIOS 세팅 템플릿 수. */
    long countByBoardModel_Id(Long boardModelId);

    boolean existsByName(String name);

    /** PUT 명칭 중복 검사 — 자기 자신은 제외. */
    boolean existsByNameAndIdNot(String name, Long id);
}
