package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.config.PxeAssetsConfig;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.engine.BootScriptDispatcher;
import com.example.serverprovision.execution.engine.BootService;
import com.example.serverprovision.execution.engine.DiagnoseLinuxExecutor;
import com.example.serverprovision.execution.engine.PhaseExecutorRegistry;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
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
@Import({ BootService.class, BootScriptDispatcher.class, PhaseExecutorRegistry.class,
        DiagnoseLinuxExecutor.class, PxeAssetsProperties.class, PxeAssetsConfig.class })
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
                .currentPhase(ProvisioningPhase.BOOTSTRAPPING).lastTransitionAt(T);
    }

    // ==== dispatch 7행 실전 — 체인로드 바디 ====================================

    @Test
    @DisplayName("개시 + 커서 DIAGNOSE_LINUX — 체인로드 스크립트 전체 계약(토큰 · URL · 폴백)")
    void chainload_fullBody() throws Exception {
        boot(progress().currentPhase(ProvisioningPhase.DIAGNOSE_LINUX).startedAt(T).build())
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
    @DisplayName("개시 + 커서 BOOTSTRAPPING — 다음 진입 대상(진단)의 체인로드 (HOLD 갇힘 회귀 방지)")
    void chainload_fromBootstrappingCursor() throws Exception {
        boot(progress().startedAt(T).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chainloading diagnose linux")));
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
