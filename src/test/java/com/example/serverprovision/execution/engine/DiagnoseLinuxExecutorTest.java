package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.engine.diagnose.DiagnoseLinuxExecutor;
import com.example.serverprovision.execution.engine.diagnose.DiagnosticReportParser;
import com.example.serverprovision.execution.engine.phase.OwnedPhasesProvider;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-1 CP4 — 체인로드 스크립트 조립 계약. 이 문자열이 곧 게스트(iPXE · Alpine init · agent.sh)와의
 * 계약이다: 커널 인자 이름(provision_token/provision_base)과 자산 URL 구조가 바뀌면 게스트가 깨진다.
 */
class DiagnoseLinuxExecutorTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 19, 2, 0);

    @TempDir Path assetsRoot;

    private DiagnoseLinuxExecutor executor;
    private com.example.serverprovision.execution.repository.GuestServerDetailRepository detailRepository;
    private ProvisioningHistoryRecorder recorder;
    private OwnedPhasesProvider ownedPhasesProvider;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        // base-url 뒤 슬래시는 properties 가 정규화 — 스크립트에 이중 슬래시가 없어야 한다.
        // 파서·mapper 는 실물(파싱 규칙까지 실검증), 저장소·원장은 mock (E1-2 소비 훅 검증).
        // 커서 전진 협력자(ES-1)는 실물 PhaseCursorAdvancer + mock OwnedPhasesProvider — 전진/종단 판정
        // (nextAfter · advanceTo · markCompleted)이 실제로 도는지까지 검증한다. 기본 공급 = 빈 집합(무할당).
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        detailRepository = org.mockito.Mockito.mock(
                com.example.serverprovision.execution.repository.GuestServerDetailRepository.class);
        recorder = org.mockito.Mockito.mock(ProvisioningHistoryRecorder.class);
        ownedPhasesProvider = org.mockito.Mockito.mock(OwnedPhasesProvider.class);
        org.mockito.BDDMockito.given(ownedPhasesProvider.ownedPhasesOf(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Set.of());
        eventPublisher = org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
        executor = new DiagnoseLinuxExecutor(
                new PxeAssetsProperties(assetsRoot.toString(), "http://10.0.2.2:7777/"),
                new DiagnosticReportParser(mapper),
                detailRepository, recorder, mapper,
                new PhaseCursorAdvancer(ownedPhasesProvider), eventPublisher,
                // E3.5-5-a — 진단 시점 RAID 봉투도 실물 파서로 정규화한다(RAID phase 와 같은 파서)
                new com.example.serverprovision.execution.engine.raid.RaidInventoryParser(mapper));
    }

    private GuestServer server(GuestToken token) {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID())
                .guestToken(token).build();
    }

    private ProvisioningProgress progress() {
        return ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.INFORMATION_COLLECTING).lastTransitionAt(T).startedAt(T).build();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("directiveFor(E3.5-1 이사) — 미수집 COLLECT · 수집됨 WAIT (접수 서비스 규칙 무변경 증인)")
    void directiveFor_movedRule() {
        GuestServer g = server(new GuestToken(TOKEN));
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.empty());
        org.assertj.core.api.Assertions.assertThat(executor.directiveFor(g, progress()))
                .isEqualTo(com.example.serverprovision.execution.enums.AgentDirective.COLLECT);

        com.example.serverprovision.execution.entity.GuestServerDetail enriched =
                org.mockito.Mockito.mock(com.example.serverprovision.execution.entity.GuestServerDetail.class);
        org.mockito.BDDMockito.given(enriched.isDiagnosticEnriched()).willReturn(true);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(enriched));
        org.assertj.core.api.Assertions.assertThat(executor.directiveFor(g, progress()))
                .isEqualTo(com.example.serverprovision.execution.enums.AgentDirective.WAIT);
    }

    @Test
    @DisplayName("체인로드 스크립트 — 자산 절대 URL · 커널 인자 계약 · EFI initrd= · 실패 폴백 전부 포함")
    void bootScript_containsFullContract() {
        String script = executor.bootScript(server(new GuestToken(TOKEN)), progress(), "systemUUID=abc");

        assertThat(script)
                .startsWith("#!ipxe")
                .contains("kernel http://10.0.2.2:7777/api/pxe/v1/assets/vmlinuz-lts")
                .contains("alpine_repo=http://10.0.2.2:7777/api/pxe/v1/assets/repo/main")
                .contains("modloop=http://10.0.2.2:7777/api/pxe/v1/assets/modloop-lts")
                .contains("apkovl=http://10.0.2.2:7777/api/pxe/v1/assets/diag.apkovl.tar.gz")
                .contains("provision_token=" + TOKEN)
                .contains("provision_base=http://10.0.2.2:7777")
                .contains("initrd=initramfs-lts")                       // EFI 필수 중복 명기(E1-R §1)
                .contains("initrd http://10.0.2.2:7777/api/pxe/v1/assets/initramfs-lts")
                .contains(":failed")                                    // 로드 실패 폴백 라벨
                .contains("chain /api/pxe/v1/boot?systemUUID=abc")      // 재진입은 원본 쿼리 그대로
                .doesNotContain("7777//");                              // base-url 정규화 검증
    }

    @Test
    @DisplayName("phase 판별자 = DIAGNOSE_LINUX (registry 위임 키)")
    void phase_isDiagnoseLinux() {
        assertThat(executor.phase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);
    }

    @Test
    @DisplayName("토큰 부재 — 등록 invariant 위반은 500 이 정직하다 (도달 불가 가드)")
    void missingToken_throwsIllegalState() {
        assertThatThrownBy(() -> executor.bootScript(server(null), progress(), "q"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("게스트 토큰 부재");
    }

    // ==== E1-2 — 수집 보고 소비(onStepClosed) =================================

    private static final String REPORT = """
            { "boardSerial": "JG4P6400027", "biosVersion": "F13",
              "cpu": {"manufacturer": "Intel", "model": "Xeon Gold 6338"},
              "memoryModules": [{"slot": "DIMM_A1", "manufacturer": "Samsung", "size": "32 GB"}],
              "disks": [{"device": "nvme0n1", "size": "1.9T", "rota": "0", "tran": "nvme"}],
              "pcieRaw": ["01:00.0 RAID bus controller: Broadcom / LSI MegaRAID 9560-8i"] }
            """;

    private com.example.serverprovision.execution.entity.GuestServerDetail realDetail(GuestServer g) {
        return com.example.serverprovision.execution.entity.GuestServerDetail.builder()
                .id(UUID.randomUUID()).guestServer(g)
                .discoveryStage(com.example.serverprovision.execution.enums.DiscoveryStage.IPXE_REGISTERED)
                .build();
    }

    private com.example.serverprovision.execution.entity.ProvisioningHistory closedCollecting(GuestServer g, String meta) {
        var step = com.example.serverprovision.execution.entity.ProvisioningHistory.openRunning(
                g, com.example.serverprovision.execution.enums.ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        step.close(com.example.serverprovision.execution.enums.ProvisioningStatus.SUCCEEDED, meta, T);
        return step;
    }

    @Test
    @DisplayName("수집 소비 — 관용 파싱 → enrich(ENRICHED 승급) → INFORMATION_PERSISTING 기록 → 완주(DEC-25)")
    void onStepClosed_enrichesAndCompletes() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));
        ProvisioningProgress p = progress();

        executor.onStepClosed(g, p, closedCollecting(g, REPORT));

        assertThat(detail.getBoardSerial()).isEqualTo("JG4P6400027");
        assertThat(detail.getDiscoveryStage())
                .isEqualTo(com.example.serverprovision.execution.enums.DiscoveryStage.DIAGNOSTIC_ENRICHED);
        assertThat(detail.getHardwareSpec()).contains("DIMM_A1").contains("MegaRAID");
        org.mockito.Mockito.verify(recorder).recordInstant(
                org.mockito.ArgumentMatchers.eq(g),
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.INFORMATION_PERSISTING),
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.execution.enums.ProvisioningStatus.SUCCEEDED),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(p.isCompleted()).isTrue();   // 무할당(빈 집합) → 진단 완주 = 종단
    }

    @Test
    @DisplayName("수집 소비(R13) — 미개시면 완주 판정 유보: 적재는 하되 커서 INFORMATION_PERSISTING 에 멈춤(수집 완료 대기)")
    void onStepClosed_notStarted_defersCompletion() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));
        ProvisioningProgress p = ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.INFORMATION_COLLECTING).lastTransitionAt(T).build();   // 미개시

        executor.onStepClosed(g, p, closedCollecting(g, REPORT));

        assertThat(detail.getDiscoveryStage())
                .isEqualTo(com.example.serverprovision.execution.enums.DiscoveryStage.DIAGNOSTIC_ENRICHED);   // 적재는 정상
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_PERSISTING);   // 유보 표식
        assertThat(p.isCompleted()).isFalse();   // 종단 아님 — 개시 시점의 소급 판정 대상
    }

    @Test
    @DisplayName("수집 소비 — 할당 게스트(ownedPhases={FIRMWARE_UPDATING}) → 인벤토리 적재 + 커서 전진(FIRMWARE_UPDATING), 종단 아님 (ES-1)")
    void onStepClosed_assignedGuest_advancesCursor() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));
        org.mockito.BDDMockito.given(ownedPhasesProvider.ownedPhasesOf(g.getId()))
                .willReturn(java.util.Set.of(ProvisioningPhase.FIRMWARE_UPDATING));
        ProvisioningProgress p = progress();

        executor.onStepClosed(g, p, closedCollecting(g, REPORT));

        // 인벤토리 적재는 무할당 경로와 동일 — 전진은 적재 이후에 얹힌다(같은 트랜잭션).
        assertThat(detail.getDiscoveryStage())
                .isEqualTo(com.example.serverprovision.execution.enums.DiscoveryStage.DIAGNOSTIC_ENRICHED);
        assertThat(p.currentPhase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);   // 진입 step 으로 pre-position
        assertThat(p.isCompleted()).isFalse();                                            // 종단 아님(HOLD 대기)
    }

    @Test
    @DisplayName("수집 소비 — placeholder 시리얼은 null 적재(필터), 나머지는 정상 (V8 대비)")
    void onStepClosed_placeholderSerialFiltered() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));

        executor.onStepClosed(g, progress(),
                closedCollecting(g, REPORT.replace("JG4P6400027", "To Be Filled By O.E.M.")));

        assertThat(detail.getBoardSerial()).isNull();
        assertThat(detail.getDiscoveryStage())
                .isEqualTo(com.example.serverprovision.execution.enums.DiscoveryStage.DIAGNOSTIC_ENRICHED);
    }

    @Test
    @DisplayName("수집 소비 — 보드 시리얼 실중복(타 서버 보유)은 시리얼만 생략(관용) — 500 루프 차단 (T1 실측 결함)")
    void onStepClosed_duplicateSerialAbsorbed() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));
        org.mockito.BDDMockito.given(detailRepository
                .existsByBoardSerialAndGuestServer_IdNotAndGuestServer_DecommissionedAtIsNull("JG4P6400027", g.getId())).willReturn(true);
        ProvisioningProgress p = progress();

        executor.onStepClosed(g, p, closedCollecting(g, REPORT));

        assertThat(detail.getBoardSerial()).isNull();                       // 중복 시리얼 생략
        assertThat(detail.getDiscoveryStage())
                .isEqualTo(com.example.serverprovision.execution.enums.DiscoveryStage.DIAGNOSTIC_ENRICHED);
        assertThat(p.isCompleted()).isTrue();                               // 나머지 파이프라인은 정상 진행
    }

    // ── E3.5-5-a — 진단 시점 RAID 봉투 소비(D2): 같은 파서 · 같은 컬럼, 실패는 관용 + 원장 filtered ──

    private static String fixture(String name) {
        try (var in = DiagnoseLinuxExecutorTest.class.getResourceAsStream("/raid/" + name)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException | NullPointerException e) {
            throw new java.io.UncheckedIOException(new java.io.IOException("픽스처 없음: " + name, e));
        }
    }

    private static String b64(String raw) {
        return java.util.Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String withRaid(String raidJson) {
        String body = REPORT.trim();
        return body.substring(0, body.length() - 1) + ", \"raid\": " + raidJson + " }";
    }

    private String persistingMetaOf(GuestServer g) {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(recorder).recordInstant(
                org.mockito.ArgumentMatchers.eq(g),
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.INFORMATION_PERSISTING),
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.execution.enums.ProvisioningStatus.SUCCEEDED),
                captor.capture(), org.mockito.ArgumentMatchers.any());
        return captor.getValue();
    }

    @Test
    @DisplayName("E3.5-5-a — 정상 RAID 봉투(IR 실측)는 RAID phase 와 같은 파서로 정규화해 raid_inventory_json 에 적재된다")
    void onStepClosed_raidEnvelope_enrichesRaidInventory() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));
        String envelope = "{\"tool\":\"sas3ircu\",\"lspci_b64\":\"" + b64(fixture("cra-lspci-nnvv.txt"))
                + "\",\"display_b64\":\"" + b64(fixture("cra-display.txt")) + "\"}";

        executor.onStepClosed(g, progress(), closedCollecting(g, withRaid(envelope)));

        assertThat(detail.getRaidInventoryJson()).contains("1458:3008").contains("MPT_IR");
        assertThat(detail.getBoardSerial()).isEqualTo("JG4P6400027");          // 하드웨어 적재는 종전 그대로
        assertThat(persistingMetaOf(g)).doesNotContain("raid(");
    }

    @Test
    @DisplayName("E3.5-5-a — reason 봉투(TOOL_MISSING)는 적재 생략 + 원장 filtered 에 사유, 진단은 성공 유지(관용)")
    void onStepClosed_raidReasonEnvelope_absorbed() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));
        ProvisioningProgress p = progress();

        executor.onStepClosed(g, p, closedCollecting(g,
                withRaid("{\"reason\":\"TOOL_MISSING\",\"detail\":\"storcli64/storcli not found\",\"lspci_b64\":\"YQ==\"}")));

        assertThat(detail.getRaidInventoryJson()).isNull();
        assertThat(detail.getDiscoveryStage())
                .isEqualTo(com.example.serverprovision.execution.enums.DiscoveryStage.DIAGNOSTIC_ENRICHED);
        assertThat(persistingMetaOf(g)).contains("raid(TOOL_MISSING)=storcli64/storcli not found");
        assertThat(p.isCompleted()).isTrue();   // 진단 완주 판정은 봉투와 무관
    }

    @Test
    @DisplayName("E3.5-5-a — 해석 불가 봉투(lspci_b64 없음)는 적재 생략 + filtered 에 unparsable, 진단은 성공 유지")
    void onStepClosed_raidUnparsableEnvelope_absorbed() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));

        executor.onStepClosed(g, progress(), closedCollecting(g, withRaid("{\"tool\":\"storcli64\"}")));

        assertThat(detail.getRaidInventoryJson()).isNull();
        assertThat(persistingMetaOf(g)).contains("raid(unparsable)=");
    }

    @Test
    @DisplayName("E3.5-5-a — raid 키가 없는 보고(카드 없는 서버)는 종전과 같다 — 적재 · filtered 흔적 없음")
    void onStepClosed_noRaidKey_unchanged() {
        GuestServer g = server(new GuestToken(TOKEN));
        var detail = realDetail(g);
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(detail));

        executor.onStepClosed(g, progress(), closedCollecting(g, REPORT));

        assertThat(detail.getRaidInventoryJson()).isNull();
        assertThat(persistingMetaOf(g)).doesNotContain("raid(");
    }

    @Test
    @DisplayName("수집 소비 — 비정형 statusMeta 는 적재·완주 없이 반환 (close 는 이미 성공 — 원장 보존, §7 관용)")
    void onStepClosed_unparsable_skipsQuietly() {
        GuestServer g = server(new GuestToken(TOKEN));
        ProvisioningProgress p = progress();

        executor.onStepClosed(g, p, closedCollecting(g, "not-json-at-all"));

        org.mockito.Mockito.verifyNoInteractions(detailRepository, recorder);
        assertThat(p.isCompleted()).isFalse();   // 다음 체크인이 COLLECT 재지시
    }

    @Test
    @DisplayName("수집 소비 — 대상 아닌 step(DIAGNOSTIC_BOOTING)은 no-op")
    void onStepClosed_ignoresOtherSteps() {
        GuestServer g = server(new GuestToken(TOKEN));
        var step = com.example.serverprovision.execution.entity.ProvisioningHistory.openRunning(
                g, com.example.serverprovision.execution.enums.ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, T);

        executor.onStepClosed(g, progress(), step);

        org.mockito.Mockito.verifyNoInteractions(detailRepository, recorder);
    }

    @Test
    @DisplayName("수집 소비 — BMC IP 가 있으면 커밋 후 소비용 이벤트를 발행한다 (E1.6 D-1)")
    void onStepClosed_bmcIp_publishesEvent() {
        GuestServer g = server(new GuestToken(TOKEN));
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(realDetail(g)));
        String withBmc = """
                { "boardSerial": "JG4P6400027", "bmc": {"ip": "10.0.0.9", "mac": "aa:bb:cc:dd:ee:ff"} }
                """;

        executor.onStepClosed(g, progress(), closedCollecting(g, withBmc));

        org.mockito.ArgumentCaptor<Object> event = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.Mockito.verify(eventPublisher).publishEvent(event.capture());
        org.assertj.core.api.Assertions.assertThat(event.getValue())
                .isEqualTo(new com.example.serverprovision.execution.event.BmcEndpointDiscoveredEvent(g.getId()));
    }

    @Test
    @DisplayName("수집 소비 — BMC IP 가 없으면 이벤트를 발행하지 않는다 (표준화 방아쇠 없음)")
    void onStepClosed_noBmcIp_noEvent() {
        GuestServer g = server(new GuestToken(TOKEN));
        org.mockito.BDDMockito.given(detailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(java.util.Optional.of(realDetail(g)));

        executor.onStepClosed(g, progress(), closedCollecting(g, REPORT));

        org.mockito.Mockito.verify(eventPublisher, org.mockito.Mockito.never())
                .publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
