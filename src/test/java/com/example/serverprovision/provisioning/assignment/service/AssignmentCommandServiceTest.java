package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentResponse;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcess;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.exception.DuplicateActiveAssignmentException;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings.FrozenBiosTemplate;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

    @Test
    @DisplayName("assign happy — 스냅샷 생성 · ownedPhases 도출 · BASIC_SETTING deep-freeze")
    void assign_success_deepFreezesBiosValues() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findById(DEF_ID)).willReturn(Optional.of(basicSettingDefinition()));
        given(assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(false);

        BoardModel board = mock(BoardModel.class);
        given(board.getId()).willReturn(3L);
        BiosSettingValues values = new BiosSettingValues(Map.of(
                BiosAttributeName.of("BootMode"), BiosAttributeValue.ofString("UEFI")));
        BiosSettingTemplate template = mock(BiosSettingTemplate.class);
        given(template.getId()).willReturn(7L);
        given(template.getBoardModel()).willReturn(board);
        given(template.getValues()).willReturn(values);
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
    @DisplayName("assign 실패 — 없는 정의서 → SettingNotFoundException(404)")
    void assign_definitionNotFound() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findById(DEF_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    @DisplayName("assign 실패 — 이미 활성 할당(중복) → DuplicateActiveAssignmentException(409)")
    void assign_duplicateActive() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(mock(GuestServer.class)));
        given(definitionRepository.findById(DEF_ID)).willReturn(Optional.of(basicSettingDefinition()));
        given(assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(true);

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(DuplicateActiveAssignmentException.class);
    }
}
