package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DuplicateSettingDefinitionNameException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspector;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * U2-3 CP4 — 쓰기 서비스 단위: 행 분해(D1)·전체 교체(D4)·name 중복(D3) + 검사기 dispatch 위임(U2-3-1).
 * 참조 가드 자체(404/409/400)는 U2-3-1 에서 inspector 단위 테스트로 이동했다(service/reference/).
 */
@ExtendWith(MockitoExtension.class)
class JpaSettingCommandServiceTest {

    @Mock SettingDefinitionRepository repository;
    @Mock ProcessReferenceInspectors referenceInspectors;
    @Mock ProcessReferenceInspector inspector;
    @InjectMocks JpaSettingCommandService service;

    private static BasicUpdateRequest autoFirmware() {
        return new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.AUTO, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null));
    }

    @Test
    @DisplayName("create — 단계별 행 분해(process_type 파생) + 단계마다 검사기 dispatch")
    void create_decomposesToRows_andDispatchesInspectors() {
        given(repository.existsByName("표준 세팅")).willReturn(false);
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.create(new SettingSaveRequest("표준 세팅",
                List.of(autoFirmware(), new BasicSettingRequest(List.of(3L, 7L)))));

        // 참조 검증은 타입별 검사기로 위임된다(U2-3-1) — 단계 수만큼 dispatch.
        verify(referenceInspectors).inspectorFor(SettingProcessType.BASIC_UPDATE);
        verify(referenceInspectors).inspectorFor(SettingProcessType.BASIC_SETTING);
        verify(inspector, times(2)).validateReferences(any(), any());

        ArgumentCaptor<SettingDefinition> captor = ArgumentCaptor.forClass(SettingDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getProcesses()).hasSize(2);
        assertThat(captor.getValue().getProcesses().get(0).getProcessType())
                .isEqualTo(SettingProcessType.BASIC_UPDATE);
        // payload 가 SSOT — 행의 파생 타입과 payload 의 다형 accessor 가 일치.
        assertThat(captor.getValue().getProcesses().get(0).getPayload().processType())
                .isEqualTo(SettingProcessType.BASIC_UPDATE);
        // 조인 테이블 파생(U2-2-3 D1) — BASIC_SETTING 행만 템플릿 참조를 갖는다.
        assertThat(captor.getValue().getProcesses().get(0).getTemplateRefs()).isEmpty();
        assertThat(captor.getValue().getProcesses().get(1).getTemplateRefs()).containsExactlyInAnyOrder(3L, 7L);
    }

    @Test
    @DisplayName("create — name 전역 중복 → 409 (검사기 dispatch 이전에 거절)")
    void create_duplicateName_throws409() {
        given(repository.existsByName("표준 세팅")).willReturn(true);

        assertThatThrownBy(() -> service.create(new SettingSaveRequest("표준 세팅",
                List.of(new BasicSettingRequest(List.of())))))
                .isInstanceOf(DuplicateSettingDefinitionNameException.class);
    }

    @Test
    @DisplayName("update — 전체 교체(D4, flush seam) + 자기 제외 중복 검사 · 없는 id 404")
    void update_replacesProcesses() {
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        SettingDefinition existing = SettingDefinition.builder()
                .name("표준 세팅")
                .processes(List.of(new com.example.serverprovision.provisioning.setting.entity.SettingProcess(
                        new com.example.serverprovision.provisioning.setting.vo.ProcessPayload(autoFirmware()))))
                .build();
        given(repository.findById(1L)).willReturn(Optional.of(existing));
        given(repository.existsByNameAndIdNot("개정 세팅", 1L)).willReturn(false);

        service.update(1L, new SettingSaveRequest("개정 세팅", List.of(new BasicSettingRequest(List.of()))));

        // flush seam — clear 선반영(UNIQUE 충돌 방지) 후 재장착이 실제로 일어났는지 확인.
        verify(repository).flush();
        assertThat(existing.getName()).isEqualTo("개정 세팅");
        assertThat(existing.getProcesses()).hasSize(1);
        assertThat(existing.getProcesses().get(0).getProcessType()).isEqualTo(SettingProcessType.BASIC_SETTING);

        given(repository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, new SettingSaveRequest("x", List.of(new BasicSettingRequest(List.of())))))
                .isInstanceOf(SettingNotFoundException.class);
    }
}
