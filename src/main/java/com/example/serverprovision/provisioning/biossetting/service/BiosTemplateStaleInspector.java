package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.biossetting.vo.BiosStaleValue;
import com.example.serverprovision.provisioning.biossetting.vo.ResolvedBiosRegistry;
import com.example.serverprovision.provisioning.exception.BiosBoardNotFoundException;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 정의서를 서버에 붙이기 전에 그 정의서의 BIOS 템플릿(서버 보드 것)이 보드 레지스트리와 정합한지 묻는 자리(E3-3 R5).
 * 판정은 {@link BiosSettingTemplate#staleAgainst} 가 하고, 여기는 정의서 → 템플릿 → 해석된 레지스트리를 잇기만 한다.
 * 문구는 {@code AssignmentBlockKind.TEMPLATE_STALE} 의 화면 tooltip 이자 서버 409 사유다.
 */
@Component
@RequiredArgsConstructor
public class BiosTemplateStaleInspector {

    private final SettingDefinitionRepository definitionRepository;
    private final BiosSettingTemplateRepository templateRepository;
    private final BiosRegistryResolver resolver;

    /** 어긋난 템플릿이 있으면 차단 문구, 없으면(보드 미확정 · 템플릿 없음 · 정합) {@code null}. */
    @Transactional(readOnly = true)
    public String reasonFor(Long definitionId, Long boardModelId) {
        if (definitionId == null || boardModelId == null) {
            return null;
        }
        SettingDefinition definition = definitionRepository.findById(definitionId).orElse(null);
        if (definition == null) {
            return null;
        }
        List<Long> templateIds = definition.getProcesses().stream()
                .map(SettingProcess::getTemplateRefs)
                .flatMap(java.util.Collection::stream)
                .distinct()
                .toList();
        List<BiosSettingTemplate> ofBoard = templateRepository.findAllById(templateIds).stream()
                .filter(t -> Objects.equals(t.getBoardModel().getId(), boardModelId))
                .toList();
        if (ofBoard.isEmpty()) {
            return null;
        }
        ResolvedBiosRegistry resolved;
        try {
            resolved = resolver.resolve(ofBoard.getFirst().getBoardModel());
        } catch (BiosBoardNotFoundException noCatalog) {
            return null;   // 자료 없는 보드 — 대조할 정본이 없으니 여기서 막지 않는다(기존 404 안전망이 편집기를 막는다)
        }
        for (BiosSettingTemplate template : ofBoard) {
            List<BiosStaleValue> stale = template.staleAgainst(resolved.registry());
            if (!stale.isEmpty()) {
                return "BIOS 템플릿 '" + template.getName() + "' 의 값이 보드 레지스트리(" + resolved.label()
                        + ")와 어긋납니다 — " + String.join(" / ", stale.stream().map(BiosStaleValue::message).toList());
            }
        }
        return null;
    }
}
