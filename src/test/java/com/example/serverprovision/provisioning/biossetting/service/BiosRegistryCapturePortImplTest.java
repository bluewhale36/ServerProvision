package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.execution.engine.setting.RegistryCheck;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.global.redfish.RedfishBiosService;
import com.example.serverprovision.global.redfish.RedfishError;
import com.example.serverprovision.global.redfish.RedfishRegistry;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import com.example.serverprovision.global.redfish.RedfishUpdateService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.biossetting.entity.BiosRegistrySnapshot;
import com.example.serverprovision.provisioning.biossetting.repository.BiosRegistrySnapshotRepository;
import com.example.serverprovision.provisioning.parser.BiosRegistryParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E3-3 R1 · R6 — 채집은 실제 BIOS 버전 키로 한 번만, 대조는 템플릿과 같은 규칙으로, 실패는 어떤 것도 밖으로 내지 않는다(Q2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BiosRegistryCapturePortImplTest {

    private static final UUID GUEST = UUID.randomUUID();
    private static final RedfishTarget TARGET = new RedfishTarget("192.168.1.130", "PG251200087");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String F44_REGISTRY = """
            {"RegistryEntries":{"Attributes":[
              {"AttributeName":"Whitley0000","Type":"Enumeration","DisplayName":"SpeedStep","ReadOnly":false,"ResetRequired":false,"DefaultValue":"Enable",
               "Value":[{"ValueName":"Disable","ValueDisplayName":"Disable"},{"ValueName":"Enable","ValueDisplayName":"Enable"}]},
              {"AttributeName":"Whitley0004","Type":"Enumeration","DisplayName":"Package C State","ReadOnly":false,"ResetRequired":false,"DefaultValue":"Auto",
               "Value":[{"ValueName":"C0/C1 state","ValueDisplayName":"C0/C1"},{"ValueName":"Auto","ValueDisplayName":"Auto"}]}
            ]}}""";

    @Mock GuestServerDetailRepository detailRepository;
    @Mock BiosRegistrySnapshotRepository snapshotRepository;
    @Mock RedfishUpdateService updateService;
    @Mock RedfishBiosService biosService;

    private BiosRegistryCapturePortImpl port;

    @BeforeEach
    void setUp() {
        port = new BiosRegistryCapturePortImpl(detailRepository, snapshotRepository, updateService, biosService,
                new BiosRegistryParser());
        BoardModel board = mock(BoardModel.class);
        given(board.getId()).willReturn(5L);
        given(board.getModelName()).willReturn("MD72-HB3");
        GuestServerDetail detail = mock(GuestServerDetail.class);
        given(detail.getBoardModel()).willReturn(board);
        given(detailRepository.findByServerIdWithBoardModel(GUEST)).willReturn(Optional.of(detail));
        given(updateService.firmwareInventory(any(), any())).willReturn(JSON.readTree("{\"Version\":\"F44\"}"));
        given(biosService.registry(any())).willReturn(
                new RedfishRegistry("BiosAttributeRegistry", "/redfish/v1/Registries/BiosAttributeRegistry.json", F44_REGISTRY));
        given(snapshotRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("첫 채집 — (보드, F44) 스냅샷을 적립하고 실기 사고의 'Disabled' 를 위반으로 잡는다(PATCH 전)")
    void firstCapture_storesAndFindsViolation() {
        given(snapshotRepository.findByBoardModel_IdAndBiosVersion(5L, "F44")).willReturn(Optional.empty());

        RegistryCheck check = port.captureAndCheck(GUEST, TARGET,
                Map.of("Whitley0000", "Disabled", "Whitley0004", "C0/C1 state"));

        ArgumentCaptor<BiosRegistrySnapshot> saved = ArgumentCaptor.forClass(BiosRegistrySnapshot.class);
        verify(snapshotRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBiosVersion()).isEqualTo("F44");
        assertThat(saved.getValue().getAttributeCount()).isEqualTo(2);
        assertThat(saved.getValue().getSourceBmcIp()).isEqualTo("192.168.1.130");
        assertThat(saved.getValue().getGuestServerId()).isEqualTo(GUEST);
        assertThat(check.available()).isTrue();
        assertThat(check.captured()).isTrue();
        assertThat(check.violations()).containsExactly("Whitley0000 = Disabled — 허용 {Disable, Enable}");
    }

    @Test
    @DisplayName("같은 키가 이미 있으면 BMC 에서 레지스트리를 다시 받지 않는다(멱등) — 대조는 저장본으로")
    void existingKey_skipsFetch() {
        BiosRegistrySnapshot existing = mock(BiosRegistrySnapshot.class);
        given(existing.getBiosVersion()).willReturn("F44");
        given(existing.getRegistryJson()).willReturn(F44_REGISTRY);
        given(snapshotRepository.findByBoardModel_IdAndBiosVersion(5L, "F44")).willReturn(Optional.of(existing));

        RegistryCheck check = port.captureAndCheck(GUEST, TARGET, Map.of("Whitley0000", "Disable"));

        verify(biosService, never()).registry(any());
        verify(snapshotRepository, never()).saveAndFlush(any());
        assertThat(check.captured()).isFalse();
        assertThat(check.hasViolations()).isFalse();
    }

    @Test
    @DisplayName("체인 실패 · 버전 미판독은 판정 없음(unavailable) — 예외를 밖으로 내지 않는다(Q2)")
    void failures_areUnavailable() {
        given(snapshotRepository.findByBoardModel_IdAndBiosVersion(5L, "F44")).willReturn(Optional.empty());
        willThrow(new RedfishRequestException(RedfishError.NOT_FOUND, "404", null)).given(biosService).registry(any());
        assertThat(port.captureAndCheck(GUEST, TARGET, Map.of("Whitley0000", "Disabled")))
                .isEqualTo(RegistryCheck.unavailable());

        given(updateService.firmwareInventory(any(), any())).willReturn(JSON.readTree("{}"));
        assertThat(port.captureAndCheck(GUEST, TARGET, Map.of()).available()).isFalse();
    }

    @Test
    @DisplayName("UNIQUE 경합 — 다른 게스트가 먼저 적립했으면 그 행을 다시 읽어 쓴다")
    void uniqueRace_rereads() {
        BiosRegistrySnapshot theirs = mock(BiosRegistrySnapshot.class);
        given(theirs.getBiosVersion()).willReturn("F44");
        given(theirs.getRegistryJson()).willReturn(F44_REGISTRY);
        given(snapshotRepository.findByBoardModel_IdAndBiosVersion(5L, "F44"))
                .willReturn(Optional.empty(), Optional.of(theirs));
        willThrow(new DataIntegrityViolationException("uk")).given(snapshotRepository).saveAndFlush(any());

        RegistryCheck check = port.captureAndCheck(GUEST, TARGET, Map.of("Whitley0000", "Enable"));

        assertThat(check.available()).isTrue();
        assertThat(check.captured()).isFalse();
    }

    @Test
    @DisplayName("captureIfAbsent — 대조 없이 적립만, 실패해도 조용하다")
    void captureIfAbsent_storesOnly() {
        given(snapshotRepository.findByBoardModel_IdAndBiosVersion(5L, "F44")).willReturn(Optional.empty());
        port.captureIfAbsent(GUEST, TARGET);
        verify(snapshotRepository).saveAndFlush(any());

        willThrow(new RuntimeException("boom")).given(biosService).registry(any());
        port.captureIfAbsent(GUEST, TARGET);   // 예외 없음
    }
}
