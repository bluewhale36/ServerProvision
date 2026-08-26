package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 펌웨어 설정 워커(E3-1 D-2 · E3-2 D-2) — 이 phase 에서 게스트가 하는 일은 재부팅 뒤 돌아왔다는 신호뿐이라 서버가
 * 주도한다. 두 축의 step 을 함께 훑어 한 대씩 독립 트랜잭션으로 넘긴다(E2-2 {@code FirmwareFlashWorker} 와 같은 결).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirmwareSettingWorker {

    private static final List<ProvisioningPhaseStep> STEPS =
            Arrays.stream(SettingAxis.values()).map(SettingAxis::getStep).toList();

    private final ProvisioningProgressRepository progressRepository;
    private final FirmwareSettingCycle cycle;

    @Scheduled(fixedDelayString = "${provision.execution.setting-worker.interval:30s}")
    public void sweep() {
        List<UUID> targets = progressRepository
                .findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(STEPS)
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
