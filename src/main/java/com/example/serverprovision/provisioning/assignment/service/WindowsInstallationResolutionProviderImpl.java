package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.windows.WindowsInstallTarget;
import com.example.serverprovision.execution.engine.windows.WindowsInstallationResolutionProvider;
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
 * ({@code RaidConfigurationResolutionProviderImpl} 과 같은 역전 자리). Windows 정의서가 아니면 empty — 다른 계열은 그
 * 계열 설치기가 맡고, 아무도 맡지 않는 계열의 사유는 OS 설치 실행기가 낸다(E4-1-b-4-1).
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallationResolutionProviderImpl implements WindowsInstallationResolutionProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;
    private final com.example.serverprovision.management.os.repository.OSMetadataRepository osMetadataRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<WindowsInstallTarget> resolveFor(UUID guestServerId) {
        return assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(snapshot -> snapshot.processRequestOf(WindowsInstallationRequest.class))
                .map(this::toTarget);
    }

    private WindowsInstallTarget toTarget(WindowsInstallationRequest windows) {
        String password = windows.getAdministratorPassword() == null ? null : windows.getAdministratorPassword().getPassword();
        return WindowsInstallTarget.windows(windows.getImageName(), password, osTargetOf(windows));
    }

    /** 정의서가 가리키는 OS 메타(R15-2 D-1) — 드라이버 변형 선택의 열쇠. 메타가 없으면(삭제 · 부재) 미상. */
    private WindowsInstallTarget.OsTarget osTargetOf(OSInstallationRequest request) {
        if (request.getOsMetadataId() == null) {
            return WindowsInstallTarget.OsTarget.UNKNOWN;
        }
        return osMetadataRepository.findById(request.getOsMetadataId())
                .map(os -> new WindowsInstallTarget.OsTarget(os.getOsName(), os.getOsVersion()))
                .orElse(WindowsInstallTarget.OsTarget.UNKNOWN);
    }
}
