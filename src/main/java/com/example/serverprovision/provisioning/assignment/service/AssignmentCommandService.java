package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentBlockKind;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentBlockKind.AssignmentBlock;
import com.example.serverprovision.provisioning.assignment.vo.AssignmentEligibility;
import com.example.serverprovision.provisioning.setting.vo.RequiredBoardModel;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.ReassignmentResponse;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.exception.DuplicateActiveAssignmentException;
import com.example.serverprovision.provisioning.assignment.exception.NoActiveAssignmentToReassignException;
import com.example.serverprovision.provisioning.assignment.exception.ReassignAfterStartException;
import com.example.serverprovision.provisioning.assignment.mapper.SettingProcessPhaseMapper;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
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
import com.example.serverprovision.provisioning.setting.exception.DefinitionNotAssignableException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 세팅 정의서 할당 · 재할당 command — 할당 시점 스냅샷 생성(행 단위 복사 · derive-then-freeze).
 *
 * <p>정의서(+processes) · 게스트를 로드(부재 · 삭제된 정의서 404)하고, 할당 가능 가드(비활성 409, U3-2-b
 * DEC-G)와 활성 유일성 가드(중복 409)를 통과하면 스냅샷을 만든다:
 * 각 {@code SettingProcess} 를 {@link AssignedProcessSnapshot} 로 payload 무변환 복사하고, BASIC_SETTING 은 참조 BIOS
 * 세팅 템플릿을 resolve 해 {@link FrozenBiosSettings} 로 deep-freeze 한다(결정 D-C). {@code ownedPhases} 는
 * {@link SettingProcessPhaseMapper} 로 derive 해 얼린다. 정의서는 <b>소프트참조</b>라 하드 FK 가 없다.</p>
 *
 * <p>재할당(U3-2-a)은 이미 있는 <b>미개시</b> 활성 스냅샷을 논리 종료(supersede)하고 현재 정의서로 새 활성
 * 스냅샷을 만든다 — 스냅샷 조립 로직은 {@link #deriveSnapshot}로 공통 추출해 할당 · 재할당이 함께 쓴다(DA5,
 * 복붙 금지). 개시된 활성의 재할당은 도메인 메서드 {@code reassignBlockReason()} SSOT 가 차단한다(DA4).</p>
 */
@Service
@RequiredArgsConstructor
public class AssignmentCommandService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentCommandService.class);

    private final SettingAssignmentSnapshotRepository assignmentRepository;
    private final SettingDefinitionRepository definitionRepository;
    private final GuestServerRepository guestServerRepository;
    private final BiosSettingTemplateRepository biosSettingTemplateRepository;
    // U3-5-a — 하드웨어 대조 입력. detail 은 서버가 보고한 하드웨어, boardModel 은 사유 문구의 보드명.
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final BoardModelRepository boardModelRepository;

    @Transactional
    public AssignmentResponse assign(UUID guestId, Long definitionId) {
        GuestServer guest = guestServerRepository.findById(guestId)
                .orElseThrow(() -> new GuestServerNotFoundException(guestId));
        SettingDefinition definition = loadAssignableDefinition(definitionId);
        assertAssignableTo(guest, definition);   // U3-5-a — 회수 · 하드웨어 대조(안전망, UI 1차 차단)

        // 활성 유일성 가드(안전망 — UI 가 재할당 폼을 1차 차단, DB unique 가 최후 방어). 정상 재할당은 reassign.
        if (assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(guestId)) {
            throw new DuplicateActiveAssignmentException(guestId);
        }

        SettingAssignmentSnapshot saved = assignmentRepository.save(deriveSnapshot(guest, definition));
        log.info("[assignment] created id={} guest={} definition={} ownedPhases={}",
                saved.getId(), guestId, definition.getId(), saved.getOwnedPhases().asSet());
        return new AssignmentResponse(
                saved.getId(), definition.getId(), definition.getName(),
                List.copyOf(saved.getOwnedPhases().asSet()));
    }

    /**
     * 재할당(U3-2-a) — 미개시 활성 스냅샷을 논리 종료(supersede)하고 현재 정의서로 새 활성 스냅샷을 만든다.
     * 게스트 · 정의서 부재는 404, 갈아끼울 활성이 없으면 409({@link NoActiveAssignmentToReassignException}),
     * 개시된 활성이면 409({@link ReassignAfterStartException}) — 차단 판정은 도메인 SSOT
     * {@code reassignBlockReason()} 하나다(뷰 disabled 와 동일 소스, DA4).
     */
    @Transactional
    public ReassignmentResponse reassign(UUID guestId, Long definitionId) {
        GuestServer guest = guestServerRepository.findById(guestId)
                .orElseThrow(() -> new GuestServerNotFoundException(guestId));
        SettingDefinition definition = loadAssignableDefinition(definitionId);

        assertAssignableTo(guest, definition);   // U3-5-a — 새 스냅샷을 만드는 것이므로 신규 할당과 같은 가드

        SettingAssignmentSnapshot active = assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(guestId)
                .orElseThrow(() -> new NoActiveAssignmentToReassignException(guestId));

        // 서버 가드(안전망) = 뷰 disabled 판정 = 단일 SSOT. 개시된 활성이면 재할당 차단(E cluster 소관).
        String blockReason = active.reassignBlockReason();
        if (blockReason != null) {
            throw new ReassignAfterStartException(guestId, blockReason);
        }

        active.supersede(LocalDateTime.now());   // 논리 종료(이력 보존) — 활성 술어 6곳이 자동으로 새 행을 가리킨다.
        // 새 활성 INSERT 전에 supersede UPDATE(active_guest_id→NULL)를 DB 에 먼저 반영한다. Hibernate 는 flush 시
        // INSERT 를 UPDATE 보다 먼저 실행하므로(IDENTITY 라 save 가 즉시 INSERT), flush 없이는 새 행과 기존 행이
        // 순간적으로 둘 다 active_guest_id=guest 가 되어 uk_active_assignment_per_guest(DA2) 를 위반한다.
        assignmentRepository.flush();
        SettingAssignmentSnapshot saved = assignmentRepository.save(deriveSnapshot(guest, definition));
        log.info("[assignment] reassigned guest={} superseded={} new={} definition={} ownedPhases={}",
                guestId, active.getId(), saved.getId(), definition.getId(), saved.getOwnedPhases().asSet());
        return new ReassignmentResponse(
                saved.getId(), active.getId(), definition.getId(), definition.getName(),
                List.copyOf(saved.getOwnedPhases().asSet()));
    }

    /**
     * 이 정의서를 이 서버에 붙일 수 있는지(U3-5-a — 할당 · 재할당 공통 안전망).
     *
     * <p>판정 순서와 사유는 {@link AssignmentBlockKind#evaluate} 하나가 갖는다. 여기서 회수 여부와
     * 하드웨어를 각각 검사하면 같은 순서가 화면 · 그룹 일괄 할당에도 복붙되고, 축이 늘 때 네 곳이 함께 자란다.
     * 예외 선택도 이 서비스가 분기하지 않고 차단 종류가 답한다.</p>
     *
     * <p>정상 흐름에서는 발동하지 않는다 — 서버 상세가 회수된 서버의 폼을 닫고 맞지 않는 정의서를 잠근다.
     * 여기까지 오는 것은 direct POST · 동시 조작 · stale 제출뿐이다.</p>
     */
    private void assertAssignableTo(GuestServer guest, SettingDefinition definition) {
        AssignmentEligibility eligibility = new AssignmentEligibility(
                guest,
                guestServerDetailRepository.findByServerIdWithBoardModel(guest.getId()).orElse(null),
                requiredBoardOf(definition));
        AssignmentBlock block = AssignmentBlockKind.evaluate(eligibility);
        if (block != null) {
            throw block.toException(guest.getId());
        }
    }

    /** 정의서가 요구하는 메인보드 — 보드명은 표시 문구에만 쓰이므로 필요할 때만 조회한다. */
    private RequiredBoardModel requiredBoardOf(SettingDefinition definition) {
        List<ProcessPayload> payloads = definition.getProcesses().stream()
                .map(SettingProcess::getPayload)
                .toList();
        return RequiredBoardModel.from(
                payloads.stream().map(ProcessPayload::request).toList(),
                boardModelId -> boardModelRepository.findById(boardModelId)
                        .map(BoardModel::getModelName)
                        .orElse("등록되지 않은 보드"));
    }

    /**
     * 할당 대상 정의서 로드 + 할당 가능 가드(U3-2-b DEC-G — 할당 · 재할당 공통).
     *
     * <p>조회부터 <b>활성(비삭제) 전용</b>이라 soft-deleted 정의서는 부재와 같이 404 로 끊긴다 — 화면에서
     * 사라진 정의서를 direct POST 로 할당하는 경로를 막는다. 이어 도메인 SSOT
     * {@code SettingDefinition.assignBlockReason()} 이 사유를 반환하면(현재는 비활성) 409 로 거절한다.
     * deprecated 정의서는 사유가 없으므로 통과한다 — 사용 중단 권고는 경고이지 차단이 아니다.</p>
     */
    private SettingDefinition loadAssignableDefinition(Long definitionId) {
        SettingDefinition definition = definitionRepository.findByIdAndIsDeletedFalse(definitionId)
                .orElseThrow(() -> new SettingNotFoundException(definitionId));
        String blockReason = definition.assignBlockReason();
        if (blockReason != null) {
            throw new DefinitionNotAssignableException(definitionId, blockReason);
        }
        return definition;
    }

    /**
     * 현재 정의서에서 활성 스냅샷을 derive-then-freeze 로 조립한다(저장은 호출자 — 할당 · 재할당 공통, DA5).
     * {@code ownedPhases} 는 {@link SettingProcessPhaseMapper} 로 도출해 얼리고, 각 {@code SettingProcess} 를
     * payload 무변환 복사하며 BASIC_SETTING 은 참조 BIOS 세팅 템플릿을 resolve 해 deep-freeze 한다.
     */
    private SettingAssignmentSnapshot deriveSnapshot(GuestServer guest, SettingDefinition definition) {
        Set<SettingProcessType> types = definition.getProcesses().stream()
                .map(SettingProcess::getProcessType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SettingProcessType.class)));
        OwnedPhases ownedPhases = SettingProcessPhaseMapper.toOwnedPhases(types);

        SettingAssignmentSnapshot assignment = SettingAssignmentSnapshot.create(
                guest,
                new SourceDefinitionRef(definition.getId(), definition.getName()),
                ownedPhases);
        for (SettingProcess process : definition.getProcesses()) {
            ProcessPayload payload = process.getPayload();   // 무변환 복사(불변 VO)
            assignment.addProcess(new AssignedProcessSnapshot(payload, freezeBiosIfPresent(payload)));
        }
        return assignment;
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
