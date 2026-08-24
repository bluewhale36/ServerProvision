package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.config.PxeAssetsConfig;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.engine.boot.BootScriptDispatcher;
import com.example.serverprovision.execution.engine.boot.BootService;
import com.example.serverprovision.execution.engine.diagnose.DiagnoseLinuxExecutor;
import com.example.serverprovision.execution.engine.phase.PhaseExecutorRegistry;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestServerRegistrationService;
import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-1 CP4 — 실물 {@code DiagnoseLinuxExecutor} + 자산 서빙을 HTTP 계층에서 검증한다.
 * {@code ExecutionRestControllerBootFlowTest}(가짜 실행기 — SPI 위임 계약)와 별도 컨텍스트인 이유:
 * 같은 phase 판별자 2개는 registry 가 기동 실패로 거부하기 때문(fail-fast 계약).
 * {@code pxe.assets.root} 조건부 활성(plan Q2)도 여기서 실제 속성 주입으로 성립한다 —
 * 속성 없는 기존 컨텍스트들에서 HOLD 가 유지되는 것이 미설정 동작의 검증이다.
 */
@WebMvcTest(controllers = ExecutionRestController.class)
@Import({
        com.example.serverprovision.execution.engine.boot.PhaseEntryGate.class,
        com.example.serverprovision.execution.engine.phase.HoldTtlPolicy.class,
        com.example.serverprovision.execution.engine.firmware.FirmwareUpdatingExecutor.class, BootService.class, BootScriptDispatcher.class, PhaseExecutorRegistry.class,
        DiagnoseLinuxExecutor.class, PxeAssetsProperties.class, PxeAssetsConfig.class,
        com.example.serverprovision.execution.engine.diagnose.DiagnosticReportParser.class,
        com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer.class })   // ES-1 — DiagnoseLinuxExecutor 협력자
class DiagnoseLinuxChainloadFlowTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final String BASE = "http://10.0.2.2:7777";
    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 19, 2, 0);

    /** 컨텍스트 기동(속성 해석) 전에 존재해야 하는 자산 디렉토리 — 정적 초기화로 준비한다. */
    private static final Path ASSETS_ROOT;
    static {
        try {
            ASSETS_ROOT = Files.createTempDirectory("pxe-assets-test");
            Files.writeString(ASSETS_ROOT.resolve("agent.sh"), "#!/bin/sh\necho agent-v1\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void pxeProperties(DynamicPropertyRegistry registry) {
        registry.add("pxe.assets.root", ASSETS_ROOT::toString);
        registry.add("pxe.server.base-url", () -> BASE);
    }

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerRegistrationService registrationService;
    @MockitoBean ProvisioningProgressRepository progressRepository;
    @MockitoBean com.example.serverprovision.execution.repository.GuestServerDetailRepository detailRepository;   // E1-2 소비 협력자
    @MockitoBean com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder provisioningHistoryRecorder;               // E1-2 소비 협력자
    @MockitoBean com.example.serverprovision.execution.engine.phase.OwnedPhasesProvider ownedPhasesProvider;           // ES-1 — PhaseCursorAdvancer 공급자
    @MockitoBean com.example.serverprovision.execution.engine.firmware.FirmwareResolutionProvider firmwareResolutionProvider;   // E2-1-b — 진입 판정 공급자
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID())
                .guestToken(new GuestToken(TOKEN)).build();
    }

    private ResultActions boot(ProvisioningProgress progress) throws Exception {
        GuestServer s = server();
        given(registrationService.initialRegistry(any(BootIPXEInfoRequest.class))).willReturn(s);
        given(progressRepository.findByGuestServer_Id(s.getId())).willReturn(Optional.of(progress));
        return mvc.perform(get("/api/pxe/v1/boot")
                .queryParam("macAddress", "aa:bb:cc:dd:ee:ff").queryParam("ipAddress", "10.20.3.11")
                .queryParam("systemUUID", "11111111-1111-1111-1111-111111111111")
                .queryParam("vendor", "Giga Computing").queryParam("boardModel", "MS03-CE0"));
    }

    private ProvisioningProgress.ProvisioningProgressBuilder progress() {
        return ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING).lastTransitionAt(T);   // ES-2 seed 계약
    }

    // ==== dispatch 7행 실전 — 체인로드 바디 ====================================

    @Test
    @DisplayName("개시 + 커서 진단 phase — 체인로드 스크립트 전체 계약(토큰 · URL · 폴백)")
    void chainload_fullBody() throws Exception {
        boot(progress().currentStep(ProvisioningPhaseStep.INFORMATION_COLLECTING).startedAt(T).build())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("#!ipxe")))
                .andExpect(content().string(containsString("kernel " + BASE + "/api/pxe/v1/assets/vmlinuz-lts")))
                .andExpect(content().string(containsString("provision_token=" + TOKEN)))
                .andExpect(content().string(containsString("provision_base=" + BASE)))
                .andExpect(content().string(containsString("initrd=initramfs-lts")))
                .andExpect(content().string(containsString(":failed")))
                .andExpect(content().string(containsString("chain /api/pxe/v1/boot?")))
                .andExpect(content().string(containsString("systemUUID=11111111")));
    }

    @Test
    @DisplayName("개시 + seed 커서(진단 진입 step) — 첫 부팅이 곧 진단 체인로드 (옛 BOOTSTRAPPING HOLD 갇힘의 원인 소멸, ES-2)")
    void chainload_fromSeedCursor() throws Exception {
        boot(progress().startedAt(T).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chainloading diagnose linux")));
    }

    // ==== ES-1 — 전진 후 /boot 재폴링 종착 (전진 = 소유 phase HOLD, 무할당 = 입고 검수) ====

    @Test
    @DisplayName("전진 커서(펌웨어 phase) — E2-1-b 로 실행기가 등록돼 미구현 HOLD 가 집행 대기로 바뀐다(재진입은 유지)")
    void advancedCursor_firmwareUpdating_awaitsFlashEngine() throws Exception {
        // ES-1 시점의 기대는 "FIRMWARE_UPDATING not implemented yet (HOLD)" 였다. 실행기 빈 등록만으로
        // dispatch 매트릭스의 그 행이 위임으로 바뀌는 것이 SPI 계약(DEC-6)이므로, 기대도 함께 옮긴다.
        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.empty());   // 할당 없음 — 판정 대상 아님

        boot(progress().currentStep(ProvisioningPhaseStep.BIOS_UPDATING).startedAt(T).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("awaiting flash engine")))
                .andExpect(content().string(containsString("chain /api/pxe/v1/boot?")));   // 재진입 유지
    }

    // ==== E2-1-b — 펌웨어 phase 의 진입 판정이 /boot 응답을 가른다 ====

    @Test
    @DisplayName("E2-1-b 준비됨 — 펌웨어 phase 진입 게스트가 집행 대기 스크립트를 받는다(해석 요약 동반)")
    void firmwarePhase_ready_awaitsFlashEngine() throws Exception {
        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.of(new com.example.serverprovision.execution.engine.firmware.FirmwareResolution(
                        com.example.serverprovision.execution.engine.firmware.AxisResolution.selected(1L, "F27", "/tmp/fw/F27.img"),
                        com.example.serverprovision.execution.engine.firmware.AxisResolution.selected(2L, "13.06.26", "/tmp/fw/13.06.26.img"))));

        boot(startedFirmwareProgress())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("firmware plan: BIOS=F27 BMC=13.06.26")))
                .andExpect(content().string(containsString("awaiting flash engine")));
    }

    @Test
    @DisplayName("E2-1-b 차단 — 무결성이 깨진 재료면 결손 대기 스크립트 + 대기 상태 전이")
    void firmwarePhase_blocked_holdsWithReason() throws Exception {
        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.of(new com.example.serverprovision.execution.engine.firmware.FirmwareResolution(
                        com.example.serverprovision.execution.engine.firmware.AxisResolution.of(
                                com.example.serverprovision.execution.engine.firmware.FirmwareAxisReason.SIGNATURE_INVALID),
                        com.example.serverprovision.execution.engine.firmware.AxisResolution.selected(2L, "13.06.26", "/tmp/fw/13.06.26.img"))));
        ProvisioningProgress progress = startedFirmwareProgress();

        boot(progress)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("waiting for resources: BIOS=SIGNATURE_INVALID")));

        org.assertj.core.api.Assertions.assertThat(progress.isHolding()).isTrue();   // 게이트가 전이시켰다
    }

    @Test
    @DisplayName("E2-1-b 건너뜀 — 한 축 결손(DEGRADED)은 대기가 아니라 진행")
    void firmwarePhase_degraded_proceeds() throws Exception {
        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.of(new com.example.serverprovision.execution.engine.firmware.FirmwareResolution(
                        com.example.serverprovision.execution.engine.firmware.AxisResolution.selected(1L, "F27", "/tmp/fw/F27.img"),
                        com.example.serverprovision.execution.engine.firmware.AxisResolution.of(
                                com.example.serverprovision.execution.engine.firmware.FirmwareAxisReason.NO_CANDIDATE))));
        ProvisioningProgress progress = startedFirmwareProgress();

        boot(progress)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("firmware plan: BIOS=F27 BMC=NO_CANDIDATE")));

        org.assertj.core.api.Assertions.assertThat(progress.isHolding()).isFalse();
    }

    /** 펌웨어 phase 진입 step 을 가리키는 개시된 진행 상태 — 전이는 도메인 메서드 통로로만 만든다. */
    private ProvisioningProgress startedFirmwareProgress() {
        ProvisioningProgress progress = progress().currentStep(ProvisioningPhaseStep.BIOS_UPDATING).build();
        progress.start(T);
        return progress;
    }

    @Test
    @DisplayName("ES-1 무할당 게스트 — 진단 완주 종단(커서 DIAGNOSE_LINUX) /boot 재폴링 → 입고 검수 대기 (현 동작 회귀)")
    void completedDiagnose_awaitsIntake() throws Exception {
        boot(progress().currentStep(ProvisioningPhaseStep.INFORMATION_PERSISTING).startedAt(T).completedAt(T).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("awaiting assignment")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("exit"))));   // OS 미설치 → exit 금지
    }

    // ==== 자산 서빙 (/assets/**) ==============================================

    @Test
    @DisplayName("자산 서빙 — 존재 파일 200 + 내용")
    void assets_existingFile_served() throws Exception {
        mvc.perform(get("/api/pxe/v1/assets/agent.sh"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("agent-v1")));
    }

    @Test
    @DisplayName("자산 서빙 — 부재 파일 404 (게스트 스크립트는 goto failed 폴백으로 회복)")
    void assets_missingFile_notFound() throws Exception {
        mvc.perform(get("/api/pxe/v1/assets/no-such-file"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("자산 서빙 — 경로 이탈(인코딩 ..%2F) 거절 (PathResourceResolver 기본 가드)")
    void assets_traversal_rejected() throws Exception {
        Files.writeString(ASSETS_ROOT.getParent().resolve("secret.txt"), "leak");
        mvc.perform(get(URI.create("/api/pxe/v1/assets/..%2Fsecret.txt")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("경로 이탈 요청은 4xx 로 거절되어야 한다")
                        .isGreaterThanOrEqualTo(400));
    }
}
