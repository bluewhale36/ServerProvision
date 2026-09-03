package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.windows.WindowsInstallTarget;
import com.example.serverprovision.execution.engine.windows.WindowsInstallationResolutionProvider;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Windows 설치 목표 공급(E4-1-a-3) — 활성 스냅샷의 OS 설치 payload 에서 이미지 이름과 Administrator 비밀번호를 꺼낸다
 * ({@code RaidConfigurationResolutionProviderImpl} 과 같은 역전 자리). 리눅스 계열 정의서는 목표가 아니라 사유로
 * 나른다 — 준비도가 "리눅스 설치는 지원하지 않습니다" 를 지목할 수 있게.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallationResolutionProviderImpl implements WindowsInstallationResolutionProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<WindowsInstallTarget> resolveFor(UUID guestServerId) {
        return assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(snapshot -> snapshot.getProcesses().stream()
                        .map(AssignedProcessSnapshot::getPayload)
                        .map(payload -> payload.request())
                        .filter(OSInstallationRequest.class::isInstance)
                        .map(OSInstallationRequest.class::cast)
                        .findFirst())
                .map(WindowsInstallationResolutionProviderImpl::toTarget);
    }

    private static WindowsInstallTarget toTarget(OSInstallationRequest request) {
        if (request instanceof WindowsInstallationRequest windows) {
            String password = windows.getAdministratorPassword() == null ? null : windows.getAdministratorPassword().getPassword();
            return WindowsInstallTarget.windows(windows.getImageName(), password);
        }
        return WindowsInstallTarget.unsupported(request.osFamily().getDisplayName());
    }
}
