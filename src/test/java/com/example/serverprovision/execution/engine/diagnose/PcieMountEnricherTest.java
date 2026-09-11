package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.engine.setting.ScriptedAmiWebApi;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.event.BmcEndpointDiscoveredEvent;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.bmcweb.AmiWebClient;
import com.example.serverprovision.global.bmcweb.AmiWebError;
import com.example.serverprovision.global.bmcweb.AmiWebRequestException;
import com.example.serverprovision.global.bmcweb.AmiWebSession;
import com.example.serverprovision.global.redfish.BmcCredentialsFallback;
import com.example.serverprovision.global.redfish.BmcCredentialsMemory;
import com.example.serverprovision.global.redfish.BmcCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HF15-6 D-1 · D-2 — 이벤트 → 비동기 시도 → 재시도 · 포기 · 인증 중단. 지연 실행기를 큐로 바꿔 시도를 손으로 굴린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PcieMountEnricherTest {

    private static final String SPEC = """
            {"cpuSockets":[{"slot":"CPU0","manufacturer":"Intel","model":"4514Y"}],"memoryModules":[],
             "disks":[{"device":"sda","type":"SSD","transport":"SAS","size":"222.6G","wwn":null}],
             "pcieDevices":[
               {"slot":"ae:00.0","kind":"RAID","vendor":"Broadcom / LSI","model":"SAS3008 PCI-Express Fusion-MPT SAS-3"},
               {"slot":"34:00.0","kind":"LAN","vendor":"Intel Corporation","model":"I210 Gigabit Network Connection"},
               {"slot":"35:00.0","kind":"LAN","vendor":"Intel Corporation","model":"I210 Gigabit Network Connection"},
               {"slot":"01:00.0","kind":"LAN_10G_SFP","vendor":"Intel Corporation","model":"X710"}]}""";

    @Mock GuestServerDetailRepository detailRepository;
    @Mock AmiWebClient webClient;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PlatformTransactionManager transactionManager;

    private final UUID serverId = UUID.randomUUID();
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private final List<Duration> delays = new ArrayList<>();
    private ScriptedAmiWebApi api;
    private GuestServerDetail detail;
    private PcieMountEnricher enricher;

    @BeforeEach
    void setUp() {
        api = new ScriptedAmiWebApi();
        AmiWebSession session = mock(AmiWebSession.class);
        given(webClient.login(any(), any())).willReturn(session);
        given(webClient.bind(session)).willReturn(api);
        detail = GuestServerDetail.builder().bmcIp(IpAddressVO.of("10.10.0.51")).boardSerial("QG260700082").hardwareSpec(SPEC).build();
        given(detailRepository.findByGuestServer_Id(serverId)).willReturn(Optional.of(detail));
        BmcCredentialsFallback fallback = new BmcCredentialsFallback(
                new BmcCredentialsResolver("admin", "standard-pw"), new BmcCredentialsMemory());
        enricher = new PcieMountEnricher(detailRepository, webClient, fallback, new ObjectMapper(), eventPublisher,
                transactionManager, null, (task, delay) -> { delays.add(delay); queue.add(task); });
    }

    private void runQueued() {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    @Test
    @DisplayName("성공 — 첫 시도에 pci_info 를 받으면 PCIe 목록만 갈아 끼우고(디스크 보존) 변화 신호를 낸다 · 로그아웃")
    void tagsOnFirstAttempt() {
        api.respond(PcieMountEnricher.PCI_INFO_PATH, PcieSlotTaggerTest.PCI_INFO);

        enricher.onBmcEndpointDiscovered(new BmcEndpointDiscoveredEvent(serverId));
        runQueued();

        assertThat(delays).containsExactly(Duration.ZERO);
        assertThat(detail.getHardwareSpec())
                .contains("\"mount\":\"ADD_IN\",\"slotDesignation\":\"PCIE_1\"")
                .contains("\"mount\":\"ONBOARD\"")
                .contains("\"mount\":\"UNKNOWN\"")            // X710 은 BMC 목록에 없다
                .contains("\"device\":\"sda\"");             // 디스크 보존
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(serverId));
        verify(webClient).logout(any());
    }

    @Test
    @DisplayName("미준비 — 빈 목록이면 30초 간격으로 5회까지 다시 시도하고, 소진하면 미확인으로 두고 아무 것도 남기지 않는다")
    void notReadyRetriesThenGivesUp() {
        api.respond(PcieMountEnricher.PCI_INFO_PATH, "[]");

        enricher.onBmcEndpointDiscovered(new BmcEndpointDiscoveredEvent(serverId));
        runQueued();

        assertThat(delays).hasSize(PcieMountEnricher.MAX_ATTEMPTS);
        assertThat(delays.getFirst()).isEqualTo(Duration.ZERO);
        assertThat(delays.getLast()).isEqualTo(PcieMountEnricher.RETRY_INTERVAL);
        assertThat(detail.getHardwareSpec()).isEqualTo(SPEC);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("오류 — BMC 거절(code 1334 류) · 프로토콜 오류는 재시도, 세 번째에 받으면 태깅")
    void rejectedThenSucceeds() {
        api.respond(PcieMountEnricher.PCI_INFO_PATH, PcieSlotTaggerTest.PCI_INFO)
                .fail("GET " + PcieMountEnricher.PCI_INFO_PATH, AmiWebError.DATA_REJECTED, 2);

        enricher.onBmcEndpointDiscovered(new BmcEndpointDiscoveredEvent(serverId));
        runQueued();

        assertThat(delays).hasSize(3);
        assertThat(detail.getHardwareSpec()).contains("\"mount\":\"ONBOARD\"");
        verify(eventPublisher).publishEvent(any(GuestServerChangedEvent.class));
    }

    @Test
    @DisplayName("인증 실패 — 후보가 전부 거부되면 즉시 멈춘다(재시도 없음 · 미확인)")
    void authFailureStops() {
        given(webClient.login(any(), any()))
                .willThrow(new AmiWebRequestException(AmiWebError.AUTH_FAILED, "POST /api/session — cc:7", 7, null));

        enricher.onBmcEndpointDiscovered(new BmcEndpointDiscoveredEvent(serverId));
        runQueued();

        assertThat(delays).hasSize(1);
        assertThat(detail.getHardwareSpec()).isEqualTo(SPEC);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("재료 없음 — BMC IP 나 PCIe 목록이 없으면 조회 자체를 하지 않는다")
    void nothingToDo() {
        detail = GuestServerDetail.builder().boardSerial("QG260700082").hardwareSpec(SPEC).build();
        given(detailRepository.findByGuestServer_Id(serverId)).willReturn(Optional.of(detail));

        enricher.onBmcEndpointDiscovered(new BmcEndpointDiscoveredEvent(serverId));
        runQueued();

        verify(webClient, never()).login(any(), any());
    }
}
