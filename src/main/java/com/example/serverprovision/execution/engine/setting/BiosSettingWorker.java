package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 설정 적용 워커(E3-1 D-2) — 이 phase 에서 게스트가 하는 일은 재부팅 뒤 돌아왔다는 신호뿐이라 서버가 주도한다.
 * 대상을 고르고 한 대씩 독립 트랜잭션으로 넘기는 일만 한다(E2-2 {@code FirmwareFlashWorker} 와 같은 결).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiosSettingWorker {

    private final ProvisioningProgressRepository progressRepository;
    private final BiosSettingCycle cycle;

    @Scheduled(fixedDelayString = "${provision.execution.setting-worker.interval:30s}")
    public void sweep() {
        List<UUID> targets = progressRepository
                .findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(List.of(ProvisioningPhaseStep.BIOS_SETTING))
                .stream().map(progress -> progress.getGuestServer().getId()).toList();
        for (UUID guestServerId : targets) {
            try {
                cycle.advance(guestServerId, LocalDateTime.now());
            } catch (Exception e) {
                log.error("[setting] {} — 적용 주기 실패", guestServerId, e);
            }
        }
    }
}
