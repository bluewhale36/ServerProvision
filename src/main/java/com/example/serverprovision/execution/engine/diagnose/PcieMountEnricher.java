package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.event.BmcEndpointDiscoveredEvent;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.global.bmcweb.AmiWebClient;
import com.example.serverprovision.global.bmcweb.AmiWebSession;
import com.example.serverprovision.global.redfish.BmcCredentialsFallback;
import com.example.serverprovision.global.redfish.BmcRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PCIe 장착 구분의 비동기 채움(HF15-6 D-1 · D-2) — 진단 수집이 BMC 접점을 적재한 뒤(AFTER_COMMIT) 별도 스레드에서
 * BMC 웹 API 의 시스템 인벤토리를 읽어 {@code hardwareSpec.pcieDevices} 에 온보드 · 추가 장착을 붙인다. BMC 는 최초
 * 접속 뒤 일정 조건이 차기 전에는 이 조회에 오류를 내므로(실기 4호 사용자 실측) 30초 간격으로 5회까지 다시 시도하고,
 * 끝내 못 받으면 "미확인" 으로 두고 아무 실패도 남기지 않는다 — 프로비저닝을 막을 정보가 아니다. 인증 실패만 즉시
 * 멈춘다(시간이 해결하지 않는다).
 */
@Slf4j
@Component
public class PcieMountEnricher {

    static final String PCI_INFO_PATH = "/api/system_inventory_gbt/pci_info";
    static final int MAX_ATTEMPTS = 5;
    static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    private final GuestServerDetailRepository detailRepository;
    private final AmiWebClient webClient;
    private final BmcCredentialsFallback credentialsFallback;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final Delayer delayer;
    private final ScheduledExecutorService scheduler;

    /** 지연 실행 — 운영은 단일 스레드 스케줄러, 테스트는 즉시 실행으로 갈아 끼운다. */
    interface Delayer {
        void later(Runnable task, Duration delay);
    }

    @Autowired
    public PcieMountEnricher(GuestServerDetailRepository detailRepository, AmiWebClient webClient,
                             BmcCredentialsFallback credentialsFallback, ObjectMapper objectMapper,
                             ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this(detailRepository, webClient, credentialsFallback, objectMapper, eventPublisher, transactionManager,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "pcie-mount-enricher");
                    t.setDaemon(true);
                    return t;
                }), null);
    }

    PcieMountEnricher(GuestServerDetailRepository detailRepository, AmiWebClient webClient,
                      BmcCredentialsFallback credentialsFallback, ObjectMapper objectMapper,
                      ApplicationEventPublisher eventPublisher, PlatformTransactionManager transactionManager,
                      ScheduledExecutorService scheduler, Delayer delayer) {
        this.detailRepository = detailRepository;
        this.webClient = webClient;
        this.credentialsFallback = credentialsFallback;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.scheduler = scheduler;
        this.delayer = delayer != null ? delayer
                : (task, delay) -> scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBmcEndpointDiscovered(BmcEndpointDiscoveredEvent event) {
        schedule(event.serverId(), 1, Duration.ZERO);
    }

    private void schedule(UUID serverId, int attempt, Duration delay) {
        delayer.later(() -> {
            try {
                attempt(serverId, attempt);
            } catch (RuntimeException e) {
                // 비동기 스레드의 예외는 아무 데도 전파되지 않는다 — 여기서 남기지 않으면 조용히 사라진다.
                log.warn("[pcie-mount] {} — 태깅 시도 {} 예상 밖 실패", serverId, attempt, e);
            }
        }, delay);
    }

    /** 시도 한 번 — 결과에 따라 다음 시도를 예약하거나 끝낸다. 패키지 공개는 테스트가 동기로 부르기 위해서다. */
    void attempt(UUID serverId, int attempt) {
        Target target = tx.execute(status -> targetOf(serverId));
        if (target == null) {
            return;
        }
        Outcome outcome = fetchAndTag(serverId, target);
        switch (outcome) {
            case TAGGED -> log.info("[pcie-mount] {} — BMC 인벤토리로 장착 구분 반영(시도 {})", serverId, attempt);
            case AUTH_FAILED -> log.warn("[pcie-mount] {} — BMC 인증 실패, 장착 구분은 미확인으로 둠", serverId);
            case NOT_READY, RETRYABLE -> {
                if (attempt >= MAX_ATTEMPTS) {
                    log.info("[pcie-mount] {} — BMC 인벤토리를 {}회 안에 받지 못해 미확인으로 둠({})", serverId, attempt, outcome);
                    return;
                }
                log.info("[pcie-mount] {} — BMC 인벤토리 {}(시도 {}), {}초 뒤 재시도", serverId, outcome, attempt, RETRY_INTERVAL.toSeconds());
                schedule(serverId, attempt + 1, RETRY_INTERVAL);
            }
        }
    }

    enum Outcome { TAGGED, NOT_READY, RETRYABLE, AUTH_FAILED }

    private record Target(String bmcIp, String boardSerial, HardwareSpec spec) {
    }

    /** 태깅 재료 — BMC 접점과 장치 목록이 있어야 한다. 없으면 null(할 일 없음). */
    private Target targetOf(UUID serverId) {
        GuestServerDetail detail = detailRepository.findByGuestServer_Id(serverId).orElse(null);
        if (detail == null || detail.getBmcIp() == null || detail.getHardwareSpec() == null) {
            return null;
        }
        HardwareSpec spec = parse(detail.getHardwareSpec());
        if (spec == null || spec.pcieDevices() == null || spec.pcieDevices().isEmpty()) {
            return null;
        }
        return new Target(detail.getBmcIp().value(), detail.getBoardSerial(), spec);
    }

    private Outcome fetchAndTag(UUID serverId, Target target) {
        RedfishTarget redfishTarget = new RedfishTarget(target.bmcIp(), target.boardSerial());
        AmiWebSession session;
        try {
            session = credentialsFallback.attempt(redfishTarget, c -> webClient.login(target.bmcIp(), c));
        } catch (BmcRequestException e) {
            log.info("[pcie-mount] {} — BMC 웹 세션 발급 실패 : {}", serverId, e.getMessage());
            return e.authFailure() ? Outcome.AUTH_FAILED : Outcome.RETRYABLE;
        }
        try {
            JsonNode pciInfo = webClient.bind(session).get(PCI_INFO_PATH);
            if (!PcieSlotTagger.looksReady(pciInfo)) {
                return Outcome.NOT_READY;
            }
            List<HardwareSpec.PcieDevice> tagged = PcieSlotTagger.tag(target.spec().pcieDevices(), pciInfo);
            tx.executeWithoutResult(status -> persist(serverId, tagged));
            return Outcome.TAGGED;
        } catch (BmcRequestException e) {
            log.info("[pcie-mount] {} — BMC 인벤토리 조회 실패 : {}", serverId, e.getMessage());
            return e.authFailure() ? Outcome.AUTH_FAILED : Outcome.RETRYABLE;
        } finally {
            webClient.logout(session);
        }
    }

    /** 최신 스펙을 다시 읽어 PCIe 목록만 갈아 끼운다 — 그 사이 디스크 · 메모리가 갱신됐어도 덮지 않는다. */
    private void persist(UUID serverId, List<HardwareSpec.PcieDevice> tagged) {
        GuestServerDetail detail = detailRepository.findByGuestServer_Id(serverId).orElse(null);
        if (detail == null || detail.getHardwareSpec() == null) {
            return;
        }
        HardwareSpec current = parse(detail.getHardwareSpec());
        if (current == null) {
            return;
        }
        HardwareSpec next = new HardwareSpec(current.cpuSockets(), current.memoryModules(), current.disks(), tagged);
        detail.updateHardwareSpec(objectMapper.writeValueAsString(next));
        eventPublisher.publishEvent(new GuestServerChangedEvent(serverId));
    }

    private HardwareSpec parse(String json) {
        try {
            return objectMapper.readValue(json, HardwareSpec.class);
        } catch (RuntimeException e) {
            log.warn("[pcie-mount] 하드웨어 스펙 JSON 해석 실패 — 태깅 생략", e);
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
