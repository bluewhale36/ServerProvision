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
        given(repository.existsByNameAndIsDeletedFalse("표준 세팅")).willReturn(false);
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
    @DisplayName("create — 활성 name 중복 → 409 (검사기 dispatch 이전에 거절, U3-2-b 활성 전용 유일성)")
    void create_duplicateName_throws409() {
        given(repository.existsByNameAndIsDeletedFalse("표준 세팅")).willReturn(true);

        assertThatThrownBy(() -> service.create(new SettingSaveRequest("표준 세팅",
                List.of(new BasicSettingRequest(List.of())))))
                .isInstanceOf(DuplicateSettingDefinitionNameException.class);
    }

    @Test
    @DisplayName("create — soft-deleted 이름은 재사용 허용(활성 전용 유일성 판정, DEC-B)")
    void create_reusesSoftDeletedName() {
        // soft-deleted 정의서가 그 이름을 갖고 있어도 existsByNameAndIsDeletedFalse 는 false → 생성 통과.
        given(repository.existsByNameAndIsDeletedFalse("재사용 세팅")).willReturn(false);
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.create(new SettingSaveRequest("재사용 세팅", List.of(new BasicSettingRequest(List.of()))));

        verify(repository).save(any());
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
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(existing));
        given(repository.existsByNameAndIdNotAndIsDeletedFalse("개정 세팅", 1L)).willReturn(false);

        service.update(1L, new SettingSaveRequest("개정 세팅", List.of(new BasicSettingRequest(List.of()))));

        // flush seam — clear 선반영(UNIQUE 충돌 방지) 후 재장착이 실제로 일어났는지 확인.
        verify(repository).flush();
        assertThat(existing.getName()).isEqualTo("개정 세팅");
        assertThat(existing.getProcesses()).hasSize(1);
        assertThat(existing.getProcesses().get(0).getProcessType()).isEqualTo(SettingProcessType.BASIC_SETTING);

        // soft-deleted(=활성 아님) 정의서는 편집 대상이 아니다 → findByIdAndIsDeletedFalse 가 빈 Optional → 404.
        given(repository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, new SettingSaveRequest("x", List.of(new BasicSettingRequest(List.of())))))
                .isInstanceOf(SettingNotFoundException.class);
    }

    // ==== U3-2-b — soft-delete lifecycle =============================

    private static SettingDefinition definitionNamed(String name) {
        return SettingDefinition.builder()
                .name(name)
                .processes(List.of(new com.example.serverprovision.provisioning.setting.entity.SettingProcess(
                        new com.example.serverprovision.provisioning.setting.vo.ProcessPayload(autoFirmware()))))
                .build();
    }

    @Test
    @DisplayName("softDelete — is_deleted 토글 + 멱등(이미 삭제된 것 재삭제해도 no-op) · 부재 404")
    void softDelete_togglesFlag_idempotent() {
        SettingDefinition def = definitionNamed("삭제 대상");
        given(repository.findById(1L)).willReturn(Optional.of(def));

        service.softDelete(1L);
        assertThat(def.isDeleted()).isTrue();

        // 멱등 — 이미 삭제된 것을 다시 삭제해도 상태 불변(예외 없음).
        service.softDelete(1L);
        assertThat(def.isDeleted()).isTrue();

        given(repository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(99L)).isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    @DisplayName("restore — happy(활성 name 충돌 없음 → 복원) · 부재 404")
    void restore_happy() {
        SettingDefinition def = definitionNamed("복원 대상");
        def.softDelete();
        given(repository.findById(1L)).willReturn(Optional.of(def));
        given(repository.existsByNameAndIsDeletedFalse("복원 대상")).willReturn(false);

        service.restore(1L);
        assertThat(def.isDeleted()).isFalse();

        given(repository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.restore(99L)).isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    @DisplayName("restore — 같은 이름의 활성 정의서 존재 → RestoreNameConflictException(409), 상태 불변")
    void restore_nameConflict_throws409() {
        SettingDefinition def = definitionNamed("충돌 이름");
        def.softDelete();
        given(repository.findById(1L)).willReturn(Optional.of(def));
        given(repository.existsByNameAndIsDeletedFalse("충돌 이름")).willReturn(true);

        assertThatThrownBy(() -> service.restore(1L))
                .isInstanceOf(com.example.serverprovision.provisioning.setting.exception.RestoreNameConflictException.class);
        assertThat(def.isDeleted()).isTrue(); // 거절 후에도 삭제 상태 유지
    }

    @Test
    @DisplayName("purge — typed-name 일치 → hard delete(자식 동반) / 불일치 → TypedNameMismatchException(400)")
    void purge_typedNameGate() {
        SettingDefinition def = definitionNamed("영구삭제 대상");
        given(repository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.of(def));

        // 불일치 → 400, delete 미호출.
        assertThatThrownBy(() -> service.purge(1L, "다른 이름"))
                .isInstanceOf(com.example.serverprovision.global.exception.TypedNameMismatchException.class);
        verify(repository, org.mockito.Mockito.never()).delete(any());

        // 일치 → delete 호출(자식 setting_process 는 cascade/orphanRemoval 동반).
        service.purge(1L, "영구삭제 대상");
        verify(repository).delete(def);
    }

    @Test
    @DisplayName("purge — 활성/부재 정의서(soft-delete 미선행) → 404 (DEC-E, findByIdAndIsDeletedTrue 빈 Optional)")
    void purge_notSoftDeleted_throws404() {
        given(repository.findByIdAndIsDeletedTrue(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.purge(5L, "무엇이든"))
                .isInstanceOf(SettingNotFoundException.class);
    }

    // ==== U3-2-b DEC-G — 활성 · 사용 중단 권고 축 ======================

    @Test
    @DisplayName("toggleEnabled — 활성 정의서를 반전 · 재호출로 복귀(멱등 아닌 토글)")
    void toggleEnabled_flipsActiveDefinition() {
        SettingDefinition def = definitionNamed("토글 대상");
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(def));

        service.toggleEnabled(1L);
        assertThat(def.isEnabled()).isFalse();
        // 비활성이어도 할당 차단 사유만 생길 뿐 정의서 자체는 그대로 편집·재활성 가능하다.
        assertThat(def.assignBlockReason()).isNotNull();

        service.toggleEnabled(1L);
        assertThat(def.isEnabled()).isTrue();
        assertThat(def.assignBlockReason()).isNull();
    }

    @Test
    @DisplayName("toggleEnabled — soft-deleted/부재 정의서는 대상 아님 → 404 (활성 전용 로드)")
    void toggleEnabled_softDeletedOrMissing_throws404() {
        // soft-deleted 정의서는 findByIdAndIsDeletedFalse 가 빈 Optional 을 준다(복원이 먼저).
        given(repository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleEnabled(99L)).isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    @DisplayName("deprecate / undeprecate — 멱등 · 활성 축 불변(deprecated ≠ disabled)")
    void deprecate_undeprecate_idempotent() {
        SettingDefinition def = definitionNamed("권고 대상");
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(def));

        service.deprecate(1L);
        service.deprecate(1L);
        assertThat(def.isDeprecated()).isTrue();
        assertThat(def.isEnabled()).isTrue();
        assertThat(def.assignBlockReason()).isNull();   // 권고는 할당을 막지 않는다

        service.undeprecate(1L);
        service.undeprecate(1L);
        assertThat(def.isDeprecated()).isFalse();
    }

    @Test
    @DisplayName("deprecate / undeprecate — soft-deleted/부재 정의서 → 404 (활성 전용 로드)")
    void deprecate_softDeletedOrMissing_throws404() {
        given(repository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deprecate(99L)).isInstanceOf(SettingNotFoundException.class);
        assertThatThrownBy(() -> service.undeprecate(99L)).isInstanceOf(SettingNotFoundException.class);
    }

    // ==== E4-1-a-2 D-11 — 수정 저장의 비밀값 병합(withSecretsRetainedFrom) =============================

    private static com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest windows(String password, boolean keep) {
        return new com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest(2L, 60L,
                new com.example.serverprovision.execution.wininstall.vo.WindowsImageName("Windows Server 2025 SERVERSTANDARD"),
                new com.example.serverprovision.provisioning.setting.dto.request.WindowsAdministratorPasswordRequest(password, keep));
    }

    private static SettingDefinition definitionWith(com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest... processes) {
        return SettingDefinition.builder()
                .name("윈도우 세팅")
                .processes(java.util.Arrays.stream(processes)
                        .map(p -> new com.example.serverprovision.provisioning.setting.entity.SettingProcess(
                                new com.example.serverprovision.provisioning.setting.vo.ProcessPayload(p)))
                        .toList())
                .build();
    }

    private static com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest storedWindows(SettingDefinition definition) {
        return (com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest)
                definition.getProcesses().get(0).getPayload().request();
    }

    @Test
    @DisplayName("update — 기존 유지(keepExistingPassword) 면 같은 단계 저장본의 Administrator 비밀번호를 이어받는다(keep 해제)")
    void update_windowsKeepExistingPassword_mergesFromStoredPayload() {
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        SettingDefinition existing = definitionWith(windows("Old!2025", false));
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(existing));
        given(repository.existsByNameAndIdNotAndIsDeletedFalse("윈도우 세팅", 1L)).willReturn(false);

        service.update(1L, new SettingSaveRequest("윈도우 세팅", List.of(windows(null, true))));

        assertThat(storedWindows(existing).getAdministratorPassword().getPassword()).isEqualTo("Old!2025");
        assertThat(storedWindows(existing).getAdministratorPassword().isKeepExistingPassword()).isFalse();
    }

    @Test
    @DisplayName("update — 새 비밀번호를 보내면 그대로 교체된다(병합 없음)")
    void update_windowsNewPassword_replaces() {
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        SettingDefinition existing = definitionWith(windows("Old!2025", false));
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(existing));
        given(repository.existsByNameAndIdNotAndIsDeletedFalse("윈도우 세팅", 1L)).willReturn(false);

        service.update(1L, new SettingSaveRequest("윈도우 세팅", List.of(windows("New!2025", false))));

        assertThat(storedWindows(existing).getAdministratorPassword().getPassword()).isEqualTo("New!2025");
    }

    @Test
    @DisplayName("update — 유지할 저장값이 없는데(단계 신설 · 구 저장본) 유지 플래그 → 400 RetainedPasswordUnavailableException, 저장본 무변")
    void update_windowsKeepWithoutExisting_throws400() {
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        SettingDefinition existing = definitionWith(new BasicSettingRequest(List.of()));
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(existing));
        given(repository.existsByNameAndIdNotAndIsDeletedFalse("윈도우 세팅", 1L)).willReturn(false);

        assertThatThrownBy(() -> service.update(1L, new SettingSaveRequest("윈도우 세팅", List.of(windows(null, true)))))
                .isInstanceOf(com.example.serverprovision.provisioning.setting.exception.RetainedPasswordUnavailableException.class);
        // 병합은 clear 전에 하므로 거절 시 기존 단계가 그대로 남는다.
        assertThat(existing.getProcesses()).hasSize(1);
        assertThat(existing.getProcesses().get(0).getProcessType()).isEqualTo(SettingProcessType.BASIC_SETTING);
    }

    // ==== HF12 — 리눅스 비밀번호 유지 병합 · updated_at touch =============================

    private static com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest rhel(String rootPassword, boolean rootKeep,
                                                                                                             String userPassword, boolean userKeep) {
        return new com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest(1L, 10L, null, null,
                new com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest(rootPassword, false, rootKeep),
                List.of(new com.example.serverprovision.provisioning.setting.dto.request.UserRequest("ops", userPassword, true, false, userKeep)),
                1L, List.of(), false, null);
    }

    private static com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest storedRhel(SettingDefinition definition) {
        return (com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest)
                definition.getProcesses().get(0).getPayload().request();
    }

    @Test
    @DisplayName("update — 리눅스 root · 사용자 유지 플래그는 같은 단계 저장본에서 값을 이어받는다(HF12 결함 B) · 저장본에 값이 없으면 400")
    void update_linuxKeepExistingPassword_mergesFromStoredPayload() {
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        SettingDefinition existing = definitionWith(rhel("R00t!", false, "Op5!", false));
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(existing));
        given(repository.existsByNameAndIdNotAndIsDeletedFalse("윈도우 세팅", 1L)).willReturn(false);

        service.update(1L, new SettingSaveRequest("윈도우 세팅", List.of(rhel(null, true, null, true))));

        assertThat(storedRhel(existing).getRootPassword().getPassword()).isEqualTo("R00t!");
        assertThat(storedRhel(existing).getRootPassword().isKeepExistingPassword()).isFalse();
        assertThat(storedRhel(existing).getUsers().get(0).getPassword()).isEqualTo("Op5!");
        assertThat(storedRhel(existing).getUsers().get(0).isKeepExistingPassword()).isFalse();

        // 구 저장본(root 값 없음)에 root 유지 → 400 · 병합은 clear 전이라 저장본 무변.
        SettingDefinition legacy = definitionWith(rhel(null, true, "Op5!", false));
        given(repository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(legacy));
        given(repository.existsByNameAndIdNotAndIsDeletedFalse("윈도우 세팅", 2L)).willReturn(false);
        assertThatThrownBy(() -> service.update(2L, new SettingSaveRequest("윈도우 세팅", List.of(rhel(null, true, null, true)))))
                .isInstanceOf(com.example.serverprovision.provisioning.setting.exception.RetainedPasswordUnavailableException.class)
                .satisfies(e -> assertThat(((com.example.serverprovision.provisioning.setting.exception.RetainedPasswordUnavailableException) e).fieldName())
                        .isEqualTo("rootPassword"));
        assertThat(storedRhel(legacy).getUsers().get(0).getPassword()).isEqualTo("Op5!");
    }

    @Test
    @DisplayName("update — 이름이 그대로여도 정의서 자신이 dirty 가 된다(touch · HF12 결함 A): 갱신 전 null 이던 updatedAt 이 채워진다")
    void update_sameName_touchesDefinition() {
        given(referenceInspectors.inspectorFor(any())).willReturn(inspector);
        SettingDefinition existing = definitionWith(new BasicSettingRequest(List.of()));
        assertThat(existing.getUpdatedAt()).isNull();   // 감사 리스너 밖(단위) — 생성 직후는 비어 있다
        given(repository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(existing));
        given(repository.existsByNameAndIdNotAndIsDeletedFalse("윈도우 세팅", 1L)).willReturn(false);

        service.update(1L, new SettingSaveRequest("윈도우 세팅", List.of(new BasicSettingRequest(List.of()))));

        assertThat(existing.getName()).isEqualTo("윈도우 세팅");
        assertThat(existing.getUpdatedAt()).isNotNull();   // 자식 교체만으로 멈추던 updated_at 이 부모 dirty 로 움직인다
    }
}
