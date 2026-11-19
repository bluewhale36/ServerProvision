package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** 계열을 모르는 쪽(실행기 · 스위퍼)이 지금 열린 OS 설치 행을 읽는 자리(E4-1-b-4-1). 누가 적은 행인지는 설치기가 판정한다. */
@Component
@RequiredArgsConstructor
public class OsInstallOpenRows {

    private final ProvisioningHistoryRepository historyRepository;

    public Optional<ProvisioningHistory> latestOpen(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guestServerId, ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING);
    }
}
