package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentResponse;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcess;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.exception.DuplicateActiveAssignmentException;
import com.example.serverprovision.provisioning.assignment.mapper.SettingProcessPhaseMapper;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings.FrozenBiosTemplate;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.vo.SourceDefinitionRef;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 세팅 정의서 할당 command — 할당 시점 스냅샷 생성(행 단위 복사 · derive-then-freeze).
 *
 * <p>정의서(+processes) · 게스트를 로드(부재 404)하고, 활성 유일성 가드(중복 409)를 통과하면 스냅샷을 만든다:
 * 각 {@code SettingProcess} 를 {@link AssignedProcess} 로 payload 무변환 복사하고, BASIC_SETTING 은 참조 BIOS
 * 세팅 템플릿을 resolve 해 {@link FrozenBiosSettings} 로 deep-freeze 한다(결정 D-C). {@code ownedPhases} 는
 * {@link SettingProcessPhaseMapper} 로 derive 해 얼린다. 정의서는 <b>소프트참조</b>라 하드 FK 가 없다.</p>
 */
@Service
@RequiredArgsConstructor
public class AssignmentCommandService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentCommandService.class);

    private final SettingAssignmentRepository assignmentRepository;
    private final SettingDefinitionRepository definitionRepository;
    private final GuestServerRepository guestServerRepository;
    private final BiosSettingTemplateRepository biosSettingTemplateRepository;

    @Transactional
    public AssignmentResponse assign(UUID guestId, Long definitionId) {
        GuestServer guest = guestServerRepository.findById(guestId)
                .orElseThrow(() -> new GuestServerNotFoundException(guestId));
        SettingDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new SettingNotFoundException(definitionId));

        // 활성 유일성 가드(안전망 — UI 가 재할당 폼을 1차 차단). 재할당 UX 는 U3-2.
        if (assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(guestId)) {
            throw new DuplicateActiveAssignmentException(guestId);
        }

        Set<SettingProcessType> types = definition.getProcesses().stream()
                .map(SettingProcess::getProcessType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SettingProcessType.class)));
        OwnedPhases ownedPhases = SettingProcessPhaseMapper.toOwnedPhases(types);   // derive-then-freeze

        SettingAssignment assignment = SettingAssignment.create(
                guest,
                new SourceDefinitionRef(definition.getId(), definition.getName()),
                ownedPhases);
        for (SettingProcess process : definition.getProcesses()) {
            ProcessPayload payload = process.getPayload();   // 무변환 복사(불변 VO)
            assignment.addProcess(new AssignedProcess(payload, freezeBiosIfPresent(payload)));
        }

        SettingAssignment saved = assignmentRepository.save(assignment);
        log.info("[assignment] created id={} guest={} definition={} ownedPhases={}",
                saved.getId(), guestId, definition.getId(), ownedPhases.asSet());
        return new AssignmentResponse(
                saved.getId(), definition.getId(), definition.getName(),
                List.copyOf(ownedPhases.asSet()));
    }

    /** BASIC_SETTING payload 의 템플릿 참조를 resolve 해 값을 동결. 그 외 타입은 null. */
    private FrozenBiosSettings freezeBiosIfPresent(ProcessPayload payload) {
        if (!(payload.request() instanceof BasicSettingRequest basicSetting)) {
            return null;
        }
        List<FrozenBiosTemplate> frozen = new ArrayList<>();
        for (Long templateId : basicSetting.getBiosSettingTemplateIds()) {
            BiosSettingTemplate template = biosSettingTemplateRepository.findById(templateId)
                    // 참조 템플릿 부재는 사용중 삭제 가드를 뚫은 데이터 손상 — 정직하게 500 으로 끊는다.
                    .orElseThrow(() -> new IllegalStateException(
                            "할당 스냅샷 동결 실패 — 참조된 BIOS 세팅 템플릿이 없습니다. templateId=" + templateId));
            frozen.add(new FrozenBiosTemplate(
                    template.getId(), template.getBoardModel().getId(), template.getValues()));
        }
        return new FrozenBiosSettings(frozen);
    }
}
