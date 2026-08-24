package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.engine.firmware.step.FlashContext;
import com.example.serverprovision.execution.engine.firmware.step.FlashStepRegistry;
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
 * 한 게스트의 집행 한 주기(E2-2) — <b>트랜잭션 경계</b>이자 사실을 모아 행에게 넘기는 자리다.
 *
 * <p>워커({@link FirmwareFlashWorker})와 별도 빈으로 나눈 이유는 {@code @Transactional} 이
 * <b>Spring proxy 를 거친 호출에서만</b> 동작하기 때문이다. 워커 안에 두면 순회 루프가 같은 클래스의
 * 메서드를 직접 부르므로 프록시를 우회해 트랜잭션이 열리지 않고, 그러면 지연 로딩 프록시를 만지는
 * 순간 세션이 없어 터진다. {@code IsoVerificationLauncher} 가 {@code @Async} 를 같은 이유로 분리한
 * 선례를 따른다.</p>
 *
 * <p>게스트마다 독립 트랜잭션인 것도 이 경계가 있어야 성립한다 — 한 대의 실패가 다른 대의 진행을
 * 되돌리지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class FirmwareFlashCycle {

    private final ProvisioningProgressRepository progressRepository;
    private final ProvisioningHistoryRepository historyRepository;
    private final GuestServerDetailRepository detailRepository;
    private final FirmwareResolutionProvider resolutionProvider;
    private final List<FirmwareUpdateProvider> providers;
    private final FlashStepRegistry stepRegistry;

    @Transactional
    public void advance(UUID guestServerId, LocalDateTime now) {
        ProvisioningProgress progress = progressRepository.findByGuestServer_Id(guestServerId).orElse(null);
        if (progress == null) {
            return;
        }
        FlashContext context = contextOf(progress, guestServerId, now);
        stepRegistry.firstMatching(context).ifPresent(step -> step.execute(context));
    }

    private FlashContext contextOf(ProvisioningProgress progress, UUID guestServerId, LocalDateTime now) {
        GuestServerDetail detail = detailRepository.findByServerIdWithBoardModel(guestServerId).orElse(null);
        List<ProvisioningHistory> history = historyRepository.findAllByServerIdOrderByStartedAt(guestServerId);
        FirmwareResolution resolution = resolutionProvider.resolveFor(guestServerId).orElse(null);
        FirmwareUpdateProvider provider = providers.stream()
                .filter(candidate -> candidate.supports(progress.getGuestServer(), detail))
                .findFirst().orElse(null);
        return new FlashContext(progress.getGuestServer(), progress, detail, history, resolution, provider, now);
    }
}
