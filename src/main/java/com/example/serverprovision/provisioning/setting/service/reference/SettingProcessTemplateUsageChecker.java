package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.provisioning.biossetting.BiosSettingTemplateUsageChecker;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link BiosSettingTemplateUsageChecker} 의 setting 측 구현 — 조인 테이블 파생 행 exists 로
 * 판정한다(U2-2-3 D4). biossetting 의 UI 플래그·409 가드가 이 판정을 공유한다.
 */
@Component
@RequiredArgsConstructor
public class SettingProcessTemplateUsageChecker implements BiosSettingTemplateUsageChecker {

    private final SettingDefinitionRepository settingDefinitionRepository;

    @Override
    public boolean isInUse(Long templateId) {
        return settingDefinitionRepository.countProcessesReferencingTemplate(templateId) > 0;
    }
}
