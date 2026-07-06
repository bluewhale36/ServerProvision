package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import com.example.serverprovision.provisioning.setting.exception.InvalidBiosTemplateSelectionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BASIC_SETTING — BIOS 세팅 템플릿·보드 참조 검사(U2-2-3).
 *
 * <ul>
 *   <li>보드(자체 selector, 2026-07-07 개정): SPECIFIED 실존 404 / disabled 409 —
 *       BASIC_UPDATE 와의 동일성은 Layer A 가 보장하므로 자체 selector 만 판정하면 충분.</li>
 *   <li>템플릿 실존: 없는 id → 404 (UI 는 실존 옵션만 렌더 — direct POST 안전망).</li>
 *   <li>보드 중복(AUTO): 같은 보드의 템플릿 2개 → 400 (실행 시 적용 대상 결정 불능).</li>
 *   <li>보드 일치(SPECIFIED): 지정 보드와 다른 보드의 템플릿 → 400.</li>
 * </ul>
 * 제조사별 다형 분기는 형성하지 않는다(U2-3-1 확정 — 차이가 실재로 확인될 때).
 */
@Component
@RequiredArgsConstructor
public class BasicSettingReferenceInspector implements ProcessReferenceInspector {

    private final BiosSettingTemplateRepository biosSettingTemplateRepository;
    private final BoardModelRepository boardModelRepository;

    @Override
    public SettingProcessType target() {
        return SettingProcessType.BASIC_SETTING;
    }

    @Override
    public void validateReferences(AbstractProcessRequest process, ProcessValidationContext context) {
        BasicSettingRequest basicSetting = (BasicSettingRequest) process;
        Long specifiedBoardId = null;
        if (basicSetting.getBoardModel() != null && !basicSetting.getBoardModel().isAuto()) {
            specifiedBoardId = basicSetting.getBoardModel().boardModelId();
            var board = boardModelRepository.findByIdAndIsDeletedFalse(specifiedBoardId)
                    .orElseThrow(() -> new BoardModelNotFoundException(
                            basicSetting.getBoardModel().boardModelId()));
            if (!board.isEnabled()) {
                throw new DisabledResourceReferenceException("boardModel", "메인보드 " + board.getModelName());
            }
        }

        Map<Long, String> seenBoards = new HashMap<>();
        for (Long templateId : basicSetting.getBiosSettingTemplateIds()) {
            BiosSettingTemplate template = biosSettingTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new BiosSettingTemplateNotFoundException(templateId));
            Long boardId = template.getBoardModel().getId();
            if (specifiedBoardId != null && !specifiedBoardId.equals(boardId)) {
                throw InvalidBiosTemplateSelectionException.boardMismatch(templateId);
            }
            String duplicated = seenBoards.putIfAbsent(boardId, template.getBoardModel().getModelName());
            if (duplicated != null) {
                throw InvalidBiosTemplateSelectionException.duplicatedBoard(duplicated);
            }
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(AbstractProcessRequest process) {
        BasicSettingRequest basicSetting = (BasicSettingRequest) process;
        List<String> names = new ArrayList<>();
        if (basicSetting.getBoardModel() != null && !basicSetting.getBoardModel().isAuto()) {
            boardModelRepository.findByIdAndIsDeletedFalse(basicSetting.getBoardModel().boardModelId())
                    .filter(LifecycleEntity::isDeprecated)
                    .ifPresent(b -> names.add("메인보드 " + b.getModelName()));
        }
        // 템플릿 자체는 lifecycle 비대상(U2-2 확정) — deprecated 개념 없음.
        return names;
    }
}
