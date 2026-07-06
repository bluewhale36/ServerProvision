package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSaveResponse;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.exception.DuplicateSettingDefinitionNameException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspectors;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * guest 세팅 정의서 쓰기 — JPA 구현 (U2-3, InMemory 스텁 대체).
 *
 * <p>정의서는 상태 없는 재사용 템플릿(Q1)이라 수정 자유·상태 가드 없음. 참조 검증(D6 — 실존/
 * disabled/정합)은 U2-3-1 에서 타입별 {@code ProcessReferenceInspector} SPI 로 다형화되어
 * 이 서비스는 dispatch 만 한다 — 신규 단계 타입·계열의 검증은 검사기 빈 추가로 흡수된다.</p>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class JpaSettingCommandService implements SettingCommandService {

    private final SettingDefinitionRepository repository;
    private final ProcessReferenceInspectors referenceInspectors;

    @Override
    public SettingSaveResponse create(SettingSaveRequest request) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw new DuplicateSettingDefinitionNameException(name);
        }
        validateReferences(request.processList());

        SettingDefinition saved = repository.save(SettingDefinition.builder()
                .name(name)
                .processes(toProcesses(request))
                .build());
        return new SettingSaveResponse(saved.getId(), saved.getName());
    }

    @Override
    public SettingSaveResponse update(Long id, SettingSaveRequest request) {
        SettingDefinition definition = repository.findById(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
        String name = request.name().trim();
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateSettingDefinitionNameException(name);
        }
        validateReferences(request.processList());

        // D4 전체 교체 — Hibernate 는 같은 flush 에서 INSERT 를 DELETE 보다 먼저 내보내므로,
        // 같은 process_type 을 재사용하는 교체가 UNIQUE(definition, type) 와 충돌한다.
        // clear 를 먼저 flush 해 orphanRemoval DELETE 를 선반영한 뒤 새 행을 장착한다.
        definition.changeNameAndClearProcesses(name);
        repository.flush();
        definition.attachProcesses(toProcesses(request));
        return new SettingSaveResponse(definition.getId(), definition.getName());
    }

    /* ─────────────────────────── 내부 조립 ─────────────────────────── */

    private List<SettingProcess> toProcesses(SettingSaveRequest request) {
        return request.processList().stream()
                .map(p -> new SettingProcess(new ProcessPayload(p)))
                .toList();
    }

    /** 단계 타입별 참조 검증 — SettingProcessType 키 dispatch (U2-3-1, instanceof 사다리 소멸). */
    private void validateReferences(List<AbstractProcessRequest> processList) {
        ProcessValidationContext context = new ProcessValidationContext(processList);
        for (AbstractProcessRequest process : processList) {
            referenceInspectors.inspectorFor(process.processType()).validateReferences(process, context);
        }
    }
}
