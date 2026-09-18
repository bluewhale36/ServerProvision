package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.global.redfish.NextBoot;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 게스트가 스스로 거는 재부팅 앞의 무장. ① REBOOT 지시 직전(HF17 · 실기 3호 F-2b) — 진단 리눅스 에이전트가 REBOOT 응답을 받으면 곧바로 자기 {@code reboot} 를
 * 하므로, 그 응답이 나가기 전에 BMC 의 다음 부팅을 {@code Once · Pxe} 로 세워야 한다. 지시를 계산하는 자리
 * ({@code AgentReportService}) 하나에서 부르므로 진단 · RAID 어느 실행기가 REBOOT 를 내든 빠지지 않는다.
 *
 * <p>무장은 <b>프로비저닝 중</b>(개시 · 미실패 · 미종단 · 미회수 — {@link GuestServerStatus#derive} 의 PROVISIONING,
 * 게이트와 같은 SSOT)일 때만 한다. 종단 · 실패의 REBOOT 은 로컬 부팅이 맞다. BMC 미검출(QEMU · 진단 전)은 세울 수단이 없다.
 * ② Windows 설치 착수 직후(HF20 G-1) — {@link #armForWindowsSetup} 가 {@code Once · Hdd} 를 세운다.</p>
 *
 * <p>BMC 호출은 DB 트랜잭션이 커밋된 뒤, 그러나 HTTP 응답이 나가기 전에 한다 — 같은 스레드의 afterCommit 동기화라
 * 에이전트는 무장이 끝난 뒤에야 REBOOT 를 받는다(비동기 워커로 보내면 무장보다 reboot 가 먼저일 수 있다). 트랜잭션이
 * 없으면(단위 테스트) 즉시 실행한다. 실패는 best effort(E2.5 D-4) — 응답은 그대로 REBOOT 이고 로그가 사유를 남긴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RebootDirectiveArmer {

    private final GuestServerDetailRepository detailRepository;
    private final RedfishPowerService powerService;

    /** 지시가 REBOOT 이고 프로비저닝 중이며 BMC 가 있으면 커밋 뒤 {@code Once · Pxe} 를 세운다. 그 외는 무동작. */
    public void armIfReboot(AgentDirective directive, GuestServer server, ProvisioningProgress progress) {
        if (directive != AgentDirective.REBOOT) {
            return;
        }
        if (GuestServerStatus.derive(progress, server.getDecommissionedAt()) != GuestServerStatus.PROVISIONING) {
            return;
        }
        GuestServerDetail detail = detailRepository.findByGuestServer_Id(server.getId()).orElse(null);
        if (detail == null || detail.getBmcIp() == null) {
            log.debug("[pxe-once] {} — REBOOT 지시인데 BMC 미검출, 무장 없음", server.getId());
            return;
        }
        RedfishTarget target = new RedfishTarget(detail.getBmcIp().value(), detail.getBoardSerial());
        UUID guestServerId = server.getId();
        afterCommit(() -> arm(guestServerId, target, NextBoot.PXE_ONCE, "[pxe-once]", "REBOOT 지시 직전"));
    }

    /**
     * Windows Setup 직전 무장(HF20 G-1) — wimboot 체인을 서빙한 직후 {@code Once · Hdd} 를 세운다. Setup 이 자기 부팅
     * 항목을 만들고 스스로 재부팅하는 POST 는 BMC 를 거치지 않으면서 BIOS 가 항목 목록을 셸 우선으로 다시 세우므로,
     * 미리 세워 둔 오버라이드만이 그 부팅을 디스크로 보낸다. 재진입(열린 서빙 행 있음)에는 부르지 않는다 — 호출자 책임.
     */
    public void armForWindowsSetup(GuestServer server) {
        GuestServerDetail detail = detailRepository.findByGuestServer_Id(server.getId()).orElse(null);
        if (detail == null || detail.getBmcIp() == null) {
            log.debug("[hdd-once] {} — Windows 설치 착수인데 BMC 미검출, 무장 없음", server.getId());
            return;
        }
        RedfishTarget target = new RedfishTarget(detail.getBmcIp().value(), detail.getBoardSerial());
        UUID guestServerId = server.getId();
        afterCommit(() -> arm(guestServerId, target, NextBoot.HDD_ONCE, "[hdd-once]", "Windows 설치 착수"));
    }

    private void arm(UUID guestServerId, RedfishTarget target, NextBoot nextBoot, String tag, String moment) {
        try {
            PowerControlResult result = powerService.armBootOverride(target, nextBoot);
            if (result.kind() == PowerControlResult.Kind.FAILED) {
                log.warn("{} {} — {} 무장 실패(best effort · 부트 순서대로 부팅될 수 있음) : {}",
                        tag, guestServerId, moment, result.message());
                return;
            }
            log.info("{} {} — {} 무장 : {}", tag, guestServerId, moment, result.message());
        } catch (RuntimeException e) {
            // 커밋 뒤의 예외가 응답을 500 으로 바꾸면 에이전트가 지시 · 스크립트를 잃는다 — 무장은 best effort 다.
            log.warn("{} {} — {} 무장 중 예외 : {}", tag, guestServerId, moment, e.toString());
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
