package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.windows.WindowsInstallTarget;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsAdministratorPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** E4-1-a-3 CP4 — 활성 스냅샷의 OS 설치 payload → Windows 목표(이미지 · 비밀번호) / 리눅스 사유 / 창 밖. */
@ExtendWith(MockitoExtension.class)
class WindowsInstallationResolutionProviderImplTest {

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final WindowsImageName STANDARD = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @InjectMocks WindowsInstallationResolutionProviderImpl provider;

    private void stubSnapshot(AbstractProcessRequest... requests) {
        List<AssignedProcessSnapshot> processes = java.util.Arrays.stream(requests).map(request -> {
            AssignedProcessSnapshot process = mock(AssignedProcessSnapshot.class);
            given(process.getPayload()).willReturn(new ProcessPayload(request));
            return process;
        }).toList();
        SettingAssignmentSnapshot snapshot = mock(SettingAssignmentSnapshot.class);
        given(snapshot.getProcesses()).willReturn(processes);
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID)).willReturn(Optional.of(snapshot));
    }

    private static OSInstallationRequest linux() {
        return new OSInstallationRequest(1L, null) {
            @Override public OSFamily osFamily() { return OSFamily.RHEL_BASED; }
        };
    }

    @Test
    @DisplayName("Windows 정의서 — 이미지 이름 · Administrator 비밀번호 평문을 나른다(toString 은 마스킹)")
    void windows_target() {
        stubSnapshot(new WindowsInstallationRequest(1L, 2L, STANDARD, new WindowsAdministratorPasswordRequest("P@ss", false)));

        Optional<WindowsInstallTarget> target = provider.resolveFor(GUEST_ID);

        assertThat(target).hasValueSatisfying(t -> {
            assertThat(t.windows()).isTrue();
            assertThat(t.imageName()).isEqualTo(STANDARD);
            assertThat(t.administratorPassword()).isEqualTo("P@ss");
            assertThat(t.toString()).doesNotContain("P@ss").contains("****");
        });
    }

    @Test
    @DisplayName("구 저장본(이미지 · 비밀번호 null) — 목표는 있으나 비어 있다(준비도가 정의서 수정을 지목)")
    void windows_legacy() {
        stubSnapshot(new WindowsInstallationRequest(1L, 2L, null, null));

        assertThat(provider.resolveFor(GUEST_ID)).hasValueSatisfying(t -> {
            assertThat(t.hasImage()).isFalse();
            assertThat(t.hasPassword()).isFalse();
        });
    }

    @Test
    @DisplayName("리눅스 정의서 — unsupported(계열 표시명)로 나른다 · 창 밖(empty)이 아니다")
    void linux_unsupported() {
        stubSnapshot(linux());

        assertThat(provider.resolveFor(GUEST_ID)).hasValueSatisfying(t -> {
            assertThat(t.windows()).isFalse();
            assertThat(t.unsupportedFamily()).isEqualTo("RHEL 계열");
        });
    }

    @Test
    @DisplayName("창 밖 — 활성 스냅샷 없음 · OS 설치 단계 없음은 empty")
    void outsideWindow() {
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID)).willReturn(Optional.empty());
        assertThat(provider.resolveFor(GUEST_ID)).isEmpty();

        stubSnapshot();
        assertThat(provider.resolveFor(GUEST_ID)).isEmpty();
    }
}
