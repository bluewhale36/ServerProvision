package com.example.serverprovision.execution.engine.firmware;

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
 * 펌웨어 집행 워커(E2-2 D-3) — 굽는 일을 주도한다.
 *
 * <p>게스트 폴링이 아니라 워커가 주도하는 이유는 이 phase 에서 <b>게스트가 하는 일이 없기</b> 때문이다.
 * 서버가 BMC 에게 시키고 게스트는 꺼져 있다. 집행을 폴링에 매달면 전원이 꺼진 동안 진행이 멈추고,
 * 게스트가 기다리는 동기 응답 안에서 수 분짜리 외부 호출을 하게 된다. 게스트 폴링이 필요한 지점은
 * 하나뿐이며 — 전원을 켠 뒤 돌아왔다는 신호 — 그것은 접촉 시각을 읽는 것으로 충분하다.</p>
 *
 * <p>그 덕에 {@code BootScriptDispatcher} 와 {@code PhaseEntryGate} 를 손대지 않는다.</p>
 *
 * <p>이 클래스는 <b>대상을 고르고 한 대씩 넘기는 일</b>만 한다. 한 주기의 트랜잭션 경계와 실제 진행은
 * {@link FirmwareFlashCycle} 이 맡는다 — 같은 클래스 안에 두면 내부 호출이 프록시를 우회해
 * 트랜잭션이 열리지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirmwareFlashWorker {

    private final ProvisioningProgressRepository progressRepository;
    private final FirmwareFlashCycle cycle;

    @Scheduled(fixedDelayString = "${provision.execution.flash-worker.interval:30s}")
    public void sweep() {
        List<UUID> targets = progressRepository
                .findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(
                        Arrays.stream(FirmwareAxis.values()).map(FirmwareAxis::getStep).toList())
                .stream().map(progress -> progress.getGuestServer().getId()).toList();

        for (UUID guestServerId : targets) {
            try {
                // 게스트마다 독립 트랜잭션 — 한 대의 실패가 다른 대를 막지 않는다.
                cycle.advance(guestServerId, LocalDateTime.now());
            } catch (Exception e) {
                log.error("[flash] {} — 집행 주기 실패", guestServerId, e);
            }
        }
    }
}
