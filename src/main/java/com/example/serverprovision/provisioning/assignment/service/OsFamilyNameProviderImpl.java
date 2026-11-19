package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.osinstall.OsFamilyNameProvider;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 활성 스냅샷 OS 설치 단계의 계열 화면 이름(E4-1-b-4-1) — 맡을 설치기가 없는 계열의 대기 사유에 쓴다. */
@Component
@RequiredArgsConstructor
public class OsFamilyNameProviderImpl implements OsFamilyNameProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> familyLabelOf(UUID guestServerId) {
        return assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(snapshot -> snapshot.processRequestOf(OSInstallationRequest.class))
                .map(request -> request.osFamily().getDisplayName());
    }
}
