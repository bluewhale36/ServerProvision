package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.event.BmcEndpointDiscoveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 계정 표준화의 비동기 진입점(E1.6 D-1) — 진단 close 커밋이 확정된 뒤(AFTER_COMMIT) 별도 스레드에서
 * 사다리를 돌린다. 게스트의 보고 HTTP 응답이 Redfish 왕복(수 초)을 기다리지 않게 하기 위해서다.
 * 본체와 빈을 나눈 이유 — {@code @Async} 는 proxy 경유 호출에서만 동작한다(IsoVerificationLauncher 선례).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmcAccountStandardizationLauncher {

    private final BmcAccountStandardizationService standardizationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBmcEndpointDiscovered(BmcEndpointDiscoveredEvent event) {
        try {
            standardizationService.standardize(event.serverId());
        } catch (Exception e) {
            // 비동기 스레드의 예외는 아무 데도 전파되지 않는다 — 여기서 남기지 않으면 조용히 사라진다.
            log.error("[bmc-account] {} — 표준화 시도 중 예상 밖 실패", event.serverId(), e);
        }
    }
}
