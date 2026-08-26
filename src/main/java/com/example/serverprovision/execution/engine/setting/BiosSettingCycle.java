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
 * 설정 적용 한 주기(E3-1 D-2) — 워커와 별도 빈인 이유는 {@code @Transactional} 이 proxy 경유에서만 열리기
 * 때문이다(E2-2 {@code FirmwareFlashCycle} 과 같은 분리). 재료를 모아 registry 에 묻고 처음 맞는 행을 실행한다.
 */
@Component
@RequiredArgsConstructor
public class BiosSettingCycle {

    private final ProvisioningProgressRepository progressRepository;
    private final ProvisioningHistoryRepository historyRepository;
    private final GuestServerDetailRepository detailRepository;
    private final BiosSettingResolutionProvider resolutionProvider;
    private final List<FirmwareUpdateProvider> providers;
    private final SettingStepRegistry stepRegistry;

    @Transactional
    public void advance(UUID guestServerId, LocalDateTime now) {
        ProvisioningProgress progress = progressRepository.findByGuestServer_Id(guestServerId).orElse(null);
        if (progress == null) {
            return;
        }
        SettingContext context = contextOf(progress, guestServerId, now);
        stepRegistry.firstMatching(context).ifPresent(step -> step.execute(context));
    }

    private SettingContext contextOf(ProvisioningProgress progress, UUID guestServerId, LocalDateTime now) {
        GuestServerDetail detail = detailRepository.findByServerIdWithBoardModel(guestServerId).orElse(null);
        List<ProvisioningHistory> history = historyRepository.findAllByServerIdOrderByStartedAt(guestServerId);
        BiosSettingTarget target = resolutionProvider.resolveFor(guestServerId).orElse(null);
        FirmwareUpdateProvider provider = providers.stream()
                .filter(candidate -> candidate.supports(progress.getGuestServer(), detail))
                .findFirst().orElse(null);
        return new SettingContext(progress.getGuestServer(), progress, detail, history, target, provider, now);
    }
}
