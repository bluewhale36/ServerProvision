package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.setting.step.SettingContext;
import com.example.serverprovision.execution.engine.setting.step.SettingStepRegistry;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 펌웨어 설정 한 주기(E3-1 D-2 · E3-2 D-2) — 워커와 별도 빈인 이유는 {@code @Transactional} 이 proxy 경유에서만
 * 열리기 때문이다(E2-2 {@code FirmwareFlashCycle} 과 같은 분리). 두 축(BIOS · BMC)의 재료를 모아 registry 에 묻고
 * 처음 맞는 행을 실행한다 — 어느 축인지는 커서 step 이 말한다.
 */
@Component
@RequiredArgsConstructor
public class FirmwareSettingCycle {

    private final ProvisioningProgressRepository progressRepository;
    private final ProvisioningHistoryRepository historyRepository;
    private final GuestServerDetailRepository detailRepository;
    private final BiosSettingResolutionProvider resolutionProvider;
    private final BmcSettingTargetResolver bmcTargetResolver;
    private final List<FirmwareUpdateProvider> providers;
    private final SettingStepRegistry stepRegistry;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.example.serverprovision.execution.engine.WorkerObservations observations;

    @Transactional
    public void advance(UUID guestServerId, LocalDateTime now) {
        ProvisioningProgress progress = progressRepository.findByGuestServer_Id(guestServerId).orElse(null);
        if (progress == null) {
            return;
        }
        // 하트비트(E2-4 Q2) — 워커가 이 게스트를 확인했다는 사실.
        observations.note(guestServerId, "설정 상태 점검", now);
        SettingContext context = contextOf(progress, guestServerId, now);
        stepRegistry.firstMatching(context).ifPresent(step -> {
            step.execute(context);
            // 설정 전이도 화면(SSE)에 닿게 한다 — FirmwareFlashCycle 과 같은 결(E2-4 R10). AFTER_COMMIT 리스너 경유.
            eventPublisher.publishEvent(
                    new com.example.serverprovision.execution.event.GuestServerChangedEvent(guestServerId));
        });
    }

    private SettingContext contextOf(ProvisioningProgress progress, UUID guestServerId, LocalDateTime now) {
        GuestServerDetail detail = detailRepository.findByServerIdWithBoardModel(guestServerId).orElse(null);
        List<ProvisioningHistory> history = historyRepository.findAllByServerIdOrderByStartedAt(guestServerId);
        BiosSettingTarget target = resolutionProvider.resolveFor(guestServerId).orElse(null);
        BmcSettingTarget bmcTarget = bmcTargetResolver.resolve(detail);
        FirmwareUpdateProvider provider = providers.stream()
                .filter(candidate -> candidate.supports(progress.getGuestServer(), detail))
                .findFirst().orElse(null);
        return new SettingContext(progress.getGuestServer(), progress, detail, history, target, bmcTarget, provider, now);
    }
}
