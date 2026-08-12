package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.ReassignmentResponse;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcess;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;
import com.example.serverprovision.provisioning.assignment.exception.DuplicateActiveAssignmentException;
import com.example.serverprovision.provisioning.assignment.exception.NoActiveAssignmentToReassignException;
import com.example.serverprovision.provisioning.assignment.exception.ReassignAfterStartException;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings.FrozenBiosTemplate;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.vo.SourceDefinitionRef;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DefinitionNotAssignableException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AssignmentCommandService} 단위 — happy(할당 + deep-freeze) + 실패(정의서 없음 · 중복 활성 · 게스트 없음).
 */
@ExtendWith(MockitoExtension.class)
class AssignmentCommandServiceTest {

    @Mock SettingAssignmentRepository assignmentRepository;
    @Mock SettingDefinitionRepository definitionRepository;
    @Mock GuestServerRepository guestServerRepository;
    @Mock BiosSettingTemplateRepository biosSettingTemplateRepository;
    // U3-5-a — 하드웨어 대조 입력. 이 fixture 의 정의서는 보드를 AUTO 로 두므로 요구 보드가 없고,
    // detail 도 비워 두어 대조가 성립하지 않는다(= 기존 시나리오의 판정 결과가 바뀌지 않는다).
    @Mock GuestServerDetailRepository guestServerDetailRepository;
    @Mock BoardModelRepository boardModelRepository;

    @InjectMocks AssignmentCommandService service;

    private static final UUID GUEST = UUID.randomUUID();
    private static final Long DEF_ID = 5L;

    /** BASIC_SETTING 단계 1개(템플릿 7 참조)를 담은 정의서 fixture. */
    private SettingDefinition basicSettingDefinition() {
        SettingProcess process = new SettingProcess(
                new ProcessPayload(new BasicSettingRequest(List.of(7L))));
        SettingDefinition definition = SettingDefinition.builder()
                .name("web-standard").processes(List.of(process)).build();
        ReflectionTestUtils.setField(definition, "id", DEF_ID);
        return definition;
    }

    /** deep-freeze 대상 BIOS 세팅 템플릿(id=7 · board=3) — 할당 · 재할당 happy 경로가 함께 쓰는 fixture. */
    private BiosSettingTemplate frozenTemplate() {
        BoardModel board = mock(BoardModel.class);
        given(board.getId()).willReturn(3L);
        BiosSettingTemplate template = mock(BiosSettingTemplate.class);
        given(template.getId()).willReturn(7L);
        given(template.getBoardModel()).willReturn(board);
        given(template.getValues()).willReturn(new BiosSettingValues(Map.of(
                BiosAttributeName.of("BootMode"), BiosAttributeValue.ofString("UEFI"))));
        return template;
    }

    @Test
    @DisplayName("assign happy — 스냅샷 생성 · ownedPhases 도출 · BASIC_SETTING deep-freeze")
    void assign_success_deepFreezesBiosValues() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(basicSettingDefinition()));
        given(assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(false);
        // 템플릿 stub 은 given(...) 밖에서 먼저 완성한다 — 중첩하면 Mockito 가 미완성 stubbing 으로 본다.
        BiosSettingTemplate template = frozenTemplate();
        given(biosSettingTemplateRepository.findById(7L)).willReturn(Optional.of(template));
        given(assignmentRepository.save(any(SettingAssignment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AssignmentResponse response = service.assign(GUEST, DEF_ID);

        // 응답 — 도출된 ownedPhases
        assertThat(response.definitionId()).isEqualTo(DEF_ID);
        assertThat(response.definitionName()).isEqualTo("web-standard");
        assertThat(response.ownedPhases()).containsExactly(ProvisioningPhase.FIRMWARE_SETTING);

        // 저장된 스냅샷 — deep-freeze 확인
        ArgumentCaptor<SettingAssignment> captor = ArgumentCaptor.forClass(SettingAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        SettingAssignment saved = captor.getValue();
        assertThat(saved.getOwnedPhases().asSet()).containsExactly(ProvisioningPhase.FIRMWARE_SETTING);
        assertThat(saved.getSourceDefinitionRef().getDefinitionId()).isEqualTo(DEF_ID);
        assertThat(saved.getProcesses()).hasSize(1);

        AssignedProcess assigned = saved.getProcesses().get(0);
        assertThat(assigned.getProcessType()).isEqualTo(SettingProcessType.BASIC_SETTING);
        assertThat(assigned.getFrozenBiosSettings()).isNotNull();
        FrozenBiosTemplate frozen = assigned.getFrozenBiosSettings().templates().get(0);
        assertThat(frozen.templateId()).isEqualTo(7L);
        assertThat(frozen.boardModelId()).isEqualTo(3L);
        assertThat(frozen.values().entries())
                .containsEntry(BiosAttributeName.of("BootMode"), BiosAttributeValue.ofString("UEFI"));
    }

    @Test
    @DisplayName("assign 실패 — 없는 게스트 → GuestServerNotFoundException(404)")
    void assign_guestNotFound() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    @Test
    @DisplayName("assign 실패 — 없는 · soft-deleted 정의서 → SettingNotFoundException(404, 활성 전용 로드)")
    void assign_definitionNotFound() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        // 삭제된 정의서도 활성 전용 조회에선 빈 Optional 이라 부재와 같은 404 로 끊긴다(U3-2-b DEC-G).
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(SettingNotFoundException.class);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign 차단 — 비활성 정의서 → DefinitionNotAssignableException(409), save 없음")
    void assign_disabledDefinition_blocked() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        SettingDefinition disabled = basicSettingDefinition();
        disabled.toggleEnabled();   // 비활성 — 신규 할당 차단(기존 스냅샷은 무영향)
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(DefinitionNotAssignableException.class)
                // 예외 메시지 = 도메인 SSOT 가 준 사유 그대로(UI 안내와 갈라지지 않게).
                .hasMessageContaining(disabled.assignBlockReason());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign 통과 — deprecated 정의서는 차단하지 않는다(사용 중단 권고는 경고일 뿐)")
    void assign_deprecatedDefinition_succeeds() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        SettingDefinition deprecated = basicSettingDefinition();
        deprecated.deprecate();
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(deprecated));
        given(assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(false);
        // 템플릿 stub 은 given(...) 밖에서 먼저 완성한다 — 중첩하면 Mockito 가 미완성 stubbing 으로 본다.
        BiosSettingTemplate template = frozenTemplate();
        given(biosSettingTemplateRepository.findById(7L)).willReturn(Optional.of(template));
        given(assignmentRepository.save(any(SettingAssignment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AssignmentResponse response = service.assign(GUEST, DEF_ID);

        assertThat(response.definitionId()).isEqualTo(DEF_ID);
        verify(assignmentRepository).save(any(SettingAssignment.class));
    }

    @Test
    @DisplayName("assign 실패 — 이미 활성 할당(중복) → DuplicateActiveAssignmentException(409)")
    void assign_duplicateActive() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(basicSettingDefinition()));
        given(assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(true);

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(DuplicateActiveAssignmentException.class);
    }

    // ==== 재할당(U3-2-a) =============================================

    /** 미개시 활성 스냅샷 fixture(id 지정) — 이전 정의서(id=9) 참조. */
    private SettingAssignment activeUnconsumed(long id) {
        SettingAssignment active = SettingAssignment.create(
                mock(GuestServer.class), new SourceDefinitionRef(9L, "old-def"), OwnedPhases.empty());
        ReflectionTestUtils.setField(active, "id", id);
        return active;
    }

    @Test
    @DisplayName("reassign happy — 미개시 활성 supersede + 현재 정의서로 새 활성(ownedPhases 재파생 · deep-freeze)")
    void reassign_success_supersedesAndCreatesNew() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(basicSettingDefinition()));

        SettingAssignment active = activeUnconsumed(100L);
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST))
                .willReturn(Optional.of(active));

        // deep-freeze 대상 템플릿(assign happy 와 동일 fixture) — 재할당도 재동결한다.
        // 템플릿 stub 은 given(...) 밖에서 먼저 완성한다 — 중첩하면 Mockito 가 미완성 stubbing 으로 본다.
        BiosSettingTemplate template = frozenTemplate();
        given(biosSettingTemplateRepository.findById(7L)).willReturn(Optional.of(template));
        given(assignmentRepository.save(any(SettingAssignment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ReassignmentResponse response = service.reassign(GUEST, DEF_ID);

        // 기존 활성은 논리 종료(supersede) — 이력 보존.
        assertThat(active.state()).isEqualTo(AssignmentState.SUPERSEDED);
        assertThat(active.isActive()).isFalse();

        // 응답 — 이전 행 id + 새 스냅샷의 재파생 ownedPhases.
        assertThat(response.supersededAssignmentId()).isEqualTo(100L);
        assertThat(response.definitionId()).isEqualTo(DEF_ID);
        assertThat(response.definitionName()).isEqualTo("web-standard");
        assertThat(response.ownedPhases()).containsExactly(ProvisioningPhase.FIRMWARE_SETTING);

        // 새 스냅샷 저장 — deep-freeze 재수행.
        ArgumentCaptor<SettingAssignment> captor = ArgumentCaptor.forClass(SettingAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        SettingAssignment saved = captor.getValue();
        assertThat(saved.getOwnedPhases().asSet()).containsExactly(ProvisioningPhase.FIRMWARE_SETTING);
        assertThat(saved.getProcesses()).hasSize(1);
        assertThat(saved.getProcesses().get(0).getFrozenBiosSettings()).isNotNull();
    }

    @Test
    @DisplayName("reassign 차단 — 개시된(ACTIVE_CONSUMED) 활성 → ReassignAfterStartException(409), supersede·save 없음")
    void reassign_consumed_blocked() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(basicSettingDefinition()));

        SettingAssignment active = activeUnconsumed(100L);
        active.markConsumed(LocalDateTime.now());   // 개시됨 → 재할당 차단 대상
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST))
                .willReturn(Optional.of(active));

        assertThatThrownBy(() -> service.reassign(GUEST, DEF_ID))
                .isInstanceOf(ReassignAfterStartException.class);

        assertThat(active.isActive()).isTrue();   // 차단 — supersede 안 됨
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassign 실패 — 갈아끼울 활성 없음 → NoActiveAssignmentToReassignException(409)")
    void reassign_noActive() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(basicSettingDefinition()));
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassign(GUEST, DEF_ID))
                .isInstanceOf(NoActiveAssignmentToReassignException.class);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassign 실패 — 없는 게스트 → GuestServerNotFoundException(404)")
    void reassign_guestNotFound() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassign(GUEST, DEF_ID))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    @Test
    @DisplayName("reassign 실패 — 없는 · soft-deleted 정의서 → SettingNotFoundException(404, 활성 전용 로드)")
    void reassign_definitionNotFound() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassign(GUEST, DEF_ID))
                .isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    @DisplayName("reassign 차단 — 비활성 정의서로 갈아끼우기 → DefinitionNotAssignableException(409), 기존 활성 보존")
    void reassign_disabledDefinition_blocked() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        SettingDefinition disabled = basicSettingDefinition();
        disabled.toggleEnabled();
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID)).willReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.reassign(GUEST, DEF_ID))
                .isInstanceOf(DefinitionNotAssignableException.class);

        // 차단은 활성 스냅샷 조회 이전이라 supersede 도 save 도 일어나지 않는다.
        verify(assignmentRepository, never()).findByGuestServer_IdAndSupersededAtIsNull(GUEST);
        verify(assignmentRepository, never()).save(any());
    }
}
