package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.engine.BootScriptDispatcher;
import com.example.serverprovision.execution.engine.BootService;
import com.example.serverprovision.execution.engine.PhaseExecutorRegistry;
import com.example.serverprovision.execution.engine.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestServerRegistrationService;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-0b CP4 — {@code /boot} text/plain dispatch 통합. 판정(BootScriptDispatcher · registry)은
 * 실빈으로 배선해 매트릭스 행별 <b>스크립트 바디 문자열</b>까지 검증한다(T0 규약).
 * 예외 → 200 재시도 스크립트 변환(컨트롤러 내장 경계)도 실경로다.
 */
@WebMvcTest(controllers = ExecutionRestController.class)
@Import({ BootService.class, BootScriptDispatcher.class, PhaseExecutorRegistry.class,
        ExecutionRestControllerBootFlowTest.FakeDiagnoseExecutor.class })
class ExecutionRestControllerBootFlowTest {

    /** dispatch 7행(실행기 위임) 검증용 가짜 실행기 — DIAGNOSE_LINUX 만 등록(다른 phase 는 HOLD 유지). */
    @TestConfiguration
    static class FakeDiagnoseExecutor {
        @Bean
        ProvisioningPhaseExecutor fakeDiagnose() {
            return new ProvisioningPhaseExecutor() {
                @Override public ProvisioningPhase phase() { return ProvisioningPhase.DIAGNOSE_LINUX; }
                @Override public String bootScript(GuestServer s, ProvisioningProgress p, String q) {
                    return "#!ipxe\n# FAKE-DIAGNOSE-CHAINLOAD q=" + q + "\n";
                }
            };
        }
    }

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerRegistrationService registrationService;
    @MockitoBean ProvisioningProgressRepository progressRepository;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 18, 12, 0);

    private GuestServer server(LocalDateTime decommissionedAt) {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID())
                .decommissionedAt(decommissionedAt).build();
    }

    private ProvisioningProgress.ProvisioningProgressBuilder progress() {
        return ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.BOOTSTRAPPING).lastTransitionAt(T);
    }

    /** 등록 결과·progress 를 고정하고 /boot 를 호출한다. */
    private ResultActions boot(GuestServer s, ProvisioningProgress p) throws Exception {
        given(registrationService.initialRegistry(any(BootIPXEInfoRequest.class))).willReturn(s);
        given(progressRepository.findByGuestServer_Id(s.getId())).willReturn(Optional.of(p));
        return mvc.perform(get("/api/pxe/v1/boot")
                .queryParam("macAddress", "aa:bb:cc:dd:ee:ff").queryParam("ipAddress", "10.20.3.11")
                .queryParam("systemUUID", "11111111-1111-1111-1111-111111111111")
                .queryParam("vendor", "Giga Computing").queryParam("boardModel", "MS03-CE0"));
    }

    // ==== dispatch 매트릭스 — 행별 스크립트 바디 ==============================

    @Test
    @DisplayName("2행 — 회수된 서버: 대기 + 회수 안내")
    void row2_decommissioned() throws Exception {
        boot(server(T), progress().build())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("#!ipxe")))
                .andExpect(content().string(containsString("decommissioned server")))
                .andExpect(content().string(containsString("chain /api/pxe/v1/boot?")))
                .andExpect(content().string(containsString("systemUUID=11111111")));
    }

    @Test
    @DisplayName("3행 — 실패 상태: 실패 지점 안내 + 운영자 대기")
    void row3_failed() throws Exception {
        boot(server(null), progress().startedAt(T).failedAt(T)
                .failedStepCode(ProvisioningPhaseStep.BIOS_UPDATING).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FAILED at BIOS_UPDATING")));
    }

    @Test
    @DisplayName("4행 — 종단: exit (로컬 부팅 폴스루, chain 없음)")
    void row4_completed() throws Exception {
        boot(server(null), progress().startedAt(T).completedAt(T).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("exit")))
                .andExpect(content().string(not(containsString("chain"))));
    }

    @Test
    @DisplayName("5행 — 미개시: 개시 게이트 대기 (DEC-26)")
    void row5_notStarted() throws Exception {
        boot(server(null), progress().build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("waiting for provisioning start")));
    }

    @Test
    @DisplayName("6행 — 실행기 미등록 phase: HOLD 명시 대기 (silent 통과 금지)")
    void row6_hold() throws Exception {
        boot(server(null), progress().startedAt(T)
                .currentPhase(ProvisioningPhase.OS_INSTALLING).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OS_INSTALLING not implemented yet (HOLD)")));
    }

    @Test
    @DisplayName("7행 — 실행기 등록 phase: 실행기 bootScript 위임 (쿼리 관통)")
    void row7_executorDelegation() throws Exception {
        boot(server(null), progress().startedAt(T)
                .currentPhase(ProvisioningPhase.DIAGNOSE_LINUX).build())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FAKE-DIAGNOSE-CHAINLOAD")))
                .andExpect(content().string(containsString("systemUUID=11111111")));
    }

    // ==== 예외 → 200 재시도 스크립트 (PXE 채널 경계) ==========================

    @Test
    @DisplayName("예외 변환 — 미등록 보드(404 계열) → 200 + 재시도 스크립트 (JSON 미노출)")
    void error_unknownBoard_becomesRetryScript() throws Exception {
        willThrow(new BoardModelNotFoundException(Vendor.GIGABYTE, "NOPE"))
                .given(registrationService).initialRegistry(any());

        mvc.perform(get("/api/pxe/v1/boot").queryParam("systemUUID", "11111111-1111-1111-1111-111111111111")
                        .queryParam("macAddress", "aa:bb:cc:dd:ee:ff").queryParam("ipAddress", "10.20.3.11")
                        .queryParam("vendor", "Giga Computing").queryParam("boardModel", "NOPE"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("server error. retrying")))
                .andExpect(content().string(not(containsString("{"))));
    }

    @Test
    @DisplayName("예외 변환 — 형식 오류(IllegalArgument) → 200 + 재시도 스크립트")
    void error_malformed_becomesRetryScript() throws Exception {
        willThrow(new IllegalArgumentException("systemUUID 형식 오류"))
                .given(registrationService).initialRegistry(any());

        mvc.perform(get("/api/pxe/v1/boot").queryParam("systemUUID", "bad"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("server error. retrying")));
    }

    @Test
    @DisplayName("예외 변환 — 낙관적 락 충돌 → 200 + 재시도 스크립트 (재부팅 폴링이 자연 재시도)")
    void error_optimisticLock_becomesRetryScript() throws Exception {
        willThrow(new OptimisticLockingFailureException("동시 갱신"))
                .given(registrationService).initialRegistry(any());

        mvc.perform(get("/api/pxe/v1/boot").queryParam("systemUUID", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("server error. retrying")));
    }
}
