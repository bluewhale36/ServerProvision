package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.global.redfish.NextBoot;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishTarget;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PXE 보장 조정자(HF15-1 · 실기 3호 F-2 · F-2b) — 게스트 상태가 바뀔 때마다 BMC 의 {@code BootSourceOverride} 를
 * {@link ProvisioningProgress#isPxeGuaranteeWindow()} 판정에 맞춘다: 창 안이면 {@code Continuous · Pxe}, 밖이면 해제.
 *
 * <p>왜 사이트마다 명시 호출이 아니라 조정자인가 — 창을 닫는 사건(종단 · 실패 · 회수 · 설치 착수)은 실행기 · 워커 · 명령
 * 서비스에 흩어져 있고, 실기 3호는 그중 한 경로(에이전트 재부팅)에 무장이 빠진 것이 결함이었다. "원하는 상태 = 진행 상태의
 * 함수" 하나를 두고 모든 변화 뒤에 그 함수로 맞추면 경로가 늘어도 빠지지 않는다. BMC 호출은 요청 스레드 밖(단일 워커)에서
 * 하고, 마지막으로 세운 값을 기억해 상태가 뒤집힐 때만 BMC 를 두드린다.</p>
 *
 * <p>실패 처리 — 세우기(Continuous) 실패는 기억하지 않아 다음 변화에서 다시 시도한다(보장이 중요). 해제 실패는 이미
 * Continuous 였던 경우만 재시도하고, 애초 상태를 모르는 게스트(기동 직후 · 미개시)의 해제 실패는 한 번으로 접는다 —
 * 꺼져 있거나 회수된 장비의 BMC 를 변화마다 두드리지 않기 위해서다. 해제가 끝내 안 돼도 {@code /boot} 의 {@code exit}
 * 폴스루가 로컬 부팅으로 이어 주므로 서비스는 멈추지 않는다.</p>
 */
@Slf4j
@Component
public class BootOverrideReconciler {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository detailRepository;
    private final ProvisioningProgressRepository progressRepository;
    private final RedfishPowerService powerService;
    private final TransactionTemplate readTx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "boot-override-reconciler");
        t.setDaemon(true);
        return t;
    });
    /** 게스트별로 마지막에 세운 값 — 뒤집힐 때만 BMC 를 부른다. */
    private final Map<UUID, NextBoot> armed = new ConcurrentHashMap<>();

    public BootOverrideReconciler(GuestServerRepository guestServerRepository, GuestServerDetailRepository detailRepository,
                                  ProvisioningProgressRepository progressRepository, RedfishPowerService powerService,
                                  PlatformTransactionManager transactionManager) {
        this.guestServerRepository = guestServerRepository;
        this.detailRepository = detailRepository;
        this.progressRepository = progressRepository;
        this.powerService = powerService;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    /** 원하는 무장과 그 대상 — {@code null} 은 "이 게스트는 조정 대상이 아니다"(BMC 미검출 · 게스트 없음). */
    record Desired(NextBoot want, RedfishTarget target) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChanged(GuestServerChangedEvent event) {
        worker.submit(() -> {
            try {
                reconcile(event.serverId());
            } catch (RuntimeException e) {
                log.warn("[pxe-guarantee] {} — 조정 중 예외(다음 변화에서 재시도) : {}", event.serverId(), e.toString());
            }
        });
    }

    /** 한 게스트를 원하는 상태에 맞춘다 — 테스트와 워커가 같은 진입점을 쓴다. */
    void reconcile(UUID guestServerId) {
        Desired desired = readTx.execute(status -> desiredOf(guestServerId));
        if (desired == null) {
            return;
        }
        NextBoot last = armed.get(guestServerId);
        if (desired.want() == last) {
            return;
        }
        PowerControlResult result = powerService.armBootOverride(desired.target(), desired.want());
        if (result.kind() != PowerControlResult.Kind.FAILED) {
            armed.put(guestServerId, desired.want());
            log.info("[pxe-guarantee] {} — {} : {}", guestServerId, desired.want().label(), result.message());
            return;
        }
        if (desired.want() == NextBoot.RELEASE && last == null) {
            armed.put(guestServerId, NextBoot.RELEASE);   // 애초 상태 미상 — 한 번 실패로 접는다(BMC 반복 호출 방지)
        }
        log.info("[pxe-guarantee] {} — {} 실패, 다음 변화에서 재시도 : {}", guestServerId, desired.want().label(), result.message());
    }

    private Desired desiredOf(UUID guestServerId) {
        GuestServer server = guestServerRepository.findById(guestServerId).orElse(null);
        if (server == null) {
            armed.remove(guestServerId);   // 영구 삭제(purge) — 기억을 지운다
            return null;
        }
        GuestServerDetail detail = detailRepository.findByGuestServer_Id(guestServerId).orElse(null);
        if (detail == null || detail.getBmcIp() == null) {
            return null;                   // BMC 미검출(QEMU · 진단 전) — 보장할 수단이 없다
        }
        ProvisioningProgress progress = progressRepository.findByGuestServer_Id(guestServerId).orElse(null);
        boolean inWindow = progress != null && server.getDecommissionedAt() == null && progress.isPxeGuaranteeWindow();
        return new Desired(inWindow ? NextBoot.PXE_CONTINUOUS : NextBoot.RELEASE,
                new RedfishTarget(detail.getBmcIp().value(), detail.getBoardSerial()));
    }

    /** 테스트 · 관측용 — 마지막에 세운 값. */
    NextBoot armedOf(UUID guestServerId) {
        return armed.get(guestServerId);
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
