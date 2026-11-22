package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** E4-1-b-4-1 — 활성 스냅숏 OS 설치 단계의 계열 화면 이름: 스냅숏 없음 · OS 단계 없음 · 있음. */
@ExtendWith(MockitoExtension.class)
class OsFamilyNameProviderImplTest {

    private static final UUID GUEST_ID = UUID.randomUUID();

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @InjectMocks OsFamilyNameProviderImpl provider;

    @Test
    @DisplayName("활성 스냅숏이 없으면 empty")
    void noSnapshot() {
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID)).willReturn(Optional.empty());
        assertThat(provider.familyLabelOf(GUEST_ID)).isEmpty();
    }

    @Test
    @DisplayName("OS 설치 단계가 없으면 empty")
    void noOsStep() {
        SettingAssignmentSnapshot snapshot = mock(SettingAssignmentSnapshot.class);
        given(snapshot.processRequestOf(OSInstallationRequest.class)).willReturn(Optional.empty());
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID)).willReturn(Optional.of(snapshot));
        assertThat(provider.familyLabelOf(GUEST_ID)).isEmpty();
    }

    @Test
    @DisplayName("OS 설치 단계가 있으면 그 계열의 화면 이름")
    void withOsStep() {
        OSInstallationRequest debian = new OSInstallationRequest(1L, 2L) {
            @Override public OSFamily osFamily() { return OSFamily.DEBIAN_BASED; }
        };
        SettingAssignmentSnapshot snapshot = mock(SettingAssignmentSnapshot.class);
        given(snapshot.processRequestOf(OSInstallationRequest.class)).willReturn(Optional.of(debian));
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID)).willReturn(Optional.of(snapshot));
        assertThat(provider.familyLabelOf(GUEST_ID)).contains("Debian 계열");
    }
}
