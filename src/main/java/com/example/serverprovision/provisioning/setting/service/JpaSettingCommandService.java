package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSaveResponse;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.exception.DuplicateSettingDefinitionNameException;
import com.example.serverprovision.provisioning.setting.exception.RestoreNameConflictException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspectors;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import java.util.stream.Collectors;
import java.util.Map;

/**
 * guest 세팅 정의서 쓰기 — JPA 구현 (U2-3, InMemory 스텁 대체).
 *
 * <p>U3-2-b 에서 삭제(softDelete/restore/purge)에 이어 활성(toggleEnabled) · 사용 중단 권고
 * (deprecate/undeprecate) 축을 더했다(DEC-G). 세 축 모두 정의서 자신의 도메인 메서드가 전이를 수행하고,
 * 이 서비스는 <b>활성 전용 로드 + 404 판정</b>만 담당한다.</p>
 *
 * <p>정의서는 실행 상태 없는 재사용 템플릿(Q1)이라 수정 자유·상태 가드 없음. 참조 검증(D6 — 실존/
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
        // 활성 전용 유일성(DEC-B) — soft-deleted 정의서의 이름은 재사용 허용.
        if (repository.existsByNameAndIsDeletedFalse(name)) {
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
        // soft-deleted 정의서는 편집 대상이 아니다(활성 전용 조회 → 없으면 404, DEC-F).
        SettingDefinition definition = activeDefinition(id);
        String name = request.name().trim();
        if (repository.existsByNameAndIdNotAndIsDeletedFalse(name, id)) {
            throw new DuplicateSettingDefinitionNameException(name);
        }
        validateReferences(request.processList());

        // E4-1-a-2 D-11 — "기존 유지" 비밀값은 clear 전에 저장본에서 이어받는다(다형 훅 · 타입 분기 없음).
        Map<SettingProcessType, AbstractProcessRequest> existing = definition.getProcesses().stream()
                .collect(Collectors.toMap(SettingProcess::getProcessType, p -> p.getPayload().request()));
        List<AbstractProcessRequest> merged = request.processList().stream()
                .map(p -> p.withSecretsRetainedFrom(existing.get(p.processType())))
                .toList();

        // D4 전체 교체 — Hibernate 는 같은 flush 에서 INSERT 를 DELETE 보다 먼저 내보내므로,
        // 같은 process_type 을 재사용하는 교체가 UNIQUE(definition, type) 와 충돌한다.
        // clear 를 먼저 flush 해 orphanRemoval DELETE 를 선반영한 뒤 새 행을 장착한다.
        definition.changeNameAndClearProcesses(name);
        repository.flush();
        definition.attachProcesses(toProcesses(merged));
        return new SettingSaveResponse(definition.getId(), definition.getName());
    }

    @Override
    public void softDelete(Long id) {
        // 멱등 — 이미 삭제된 것도 찾아 no-op 처리(재삭제해도 상태 불변). 부재만 404.
        SettingDefinition definition = repository.findById(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
        definition.softDelete();
    }

    @Override
    public void restore(Long id) {
        SettingDefinition definition = repository.findById(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
        // 활성 name 충돌 가드(DEC-B) — 삭제된 자신은 활성 술어에서 제외되므로, 여기 걸리는 것은 그새 같은
        // 이름을 점유한 다른 활성 정의서다(UI 1차 차단을 뚫은 stale/동시성 경로의 서버 안전망).
        if (repository.existsByNameAndIsDeletedFalse(definition.getName())) {
            throw new RestoreNameConflictException(definition.getName());
        }
        definition.restore();
    }

    @Override
    public void purge(Long id, String typedName) {
        // soft-delete 선행 강제(DEC-E) — 활성/부재는 빈 Optional → 404. TypedNameGuard(Markable 전용) 대신
        // 파일 없는 정의서라 name.equals 로 직접 검증한다(TypedNameMismatchException 재사용).
        SettingDefinition definition = repository.findByIdAndIsDeletedTrue(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
        if (!definition.getName().equals(typedName)) {
            throw new TypedNameMismatchException(definition.getName(), typedName);
        }
        // hard delete — 자식 setting_process 는 cascade + orphanRemoval 로 동반 삭제. 소프트참조 할당
        // 스냅샷(FK 없음)은 이 삭제와 무관하게 생존한다(DEC-C).
        repository.delete(definition);
    }

    @Override
    public void toggleEnabled(Long id) {
        activeDefinition(id).toggleEnabled();
    }

    @Override
    public void deprecate(Long id) {
        activeDefinition(id).deprecate();
    }

    @Override
    public void undeprecate(Long id) {
        activeDefinition(id).undeprecate();
    }

    /* ─────────────────────────── 내부 조립 ─────────────────────────── */

    /**
     * enabled/deprecated 축을 다루는 액션의 공통 로드(U3-2-b DEC-G) — soft-deleted 정의서는 상세에서 복원 ·
     * 영구삭제만 노출하므로 대상이 아니다(활성 전용 조회 → 없으면 404, update 와 같은 규약).
     */
    private SettingDefinition activeDefinition(Long id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
    }

    private List<SettingProcess> toProcesses(SettingSaveRequest request) {
        return toProcesses(request.processList());
    }

    private List<SettingProcess> toProcesses(List<AbstractProcessRequest> processList) {
        return processList.stream()
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
