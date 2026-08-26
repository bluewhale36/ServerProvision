package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.redfish.BmcCredentials;
import com.example.serverprovision.global.redfish.BmcCredentialsResolver;
import com.example.serverprovision.global.redfish.RedfishAccountService;
import com.example.serverprovision.global.redfish.RedfishError;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E1.6 CP4 — 표준화 사다리(D-2)의 갈래별 검증. 판정 기준은 "어느 자격으로 열렸는가"이며,
 * 원장은 최초 전환(SUCCEEDED)과 운영자 개입 필요(FAILED)만 남고 일시 상태는 침묵한다.
 */
class BmcAccountStandardizationServiceTest {

    private static final UUID SERVER_ID = UUID.randomUUID();
    private static final String SERIAL = "QG260700082";

    private GuestServerRepository serverRepository;
    private GuestServerDetailRepository detailRepository;
    private RedfishAccountService accountService;
    private FirmwareUpdateProvider provider;
    private ProvisioningHistoryRecorder recorder;
    private BmcAccountStandardizationService service;

    private final ObjectMapper mapper = new ObjectMapper();
    private final BmcCredentials standard = new BmcCredentials("admin", "standard-pw", "표준 계정");

    @BeforeEach
    void setUp() {
        serverRepository = mock(GuestServerRepository.class);
        detailRepository = mock(GuestServerDetailRepository.class);
        accountService = mock(RedfishAccountService.class);
        provider = mock(FirmwareUpdateProvider.class);
        recorder = mock(ProvisioningHistoryRecorder.class);
        service = new BmcAccountStandardizationService(serverRepository, detailRepository,
                new BmcCredentialsResolver("admin", "standard-pw"), accountService, provider, recorder, mapper);

        GuestServer server = GuestServer.builder().id(SERVER_ID).build();
        given(serverRepository.findById(SERVER_ID)).willReturn(Optional.of(server));
        GuestServerDetail detail = mock(GuestServerDetail.class);
        given(detail.getBmcIp()).willReturn(new IpAddressVO("10.0.0.9"));
        given(detail.getBoardSerial()).willReturn(SERIAL);
        given(detailRepository.findByServerIdWithBoardModel(SERVER_ID)).willReturn(Optional.of(detail));
    }

    private static RedfishRequestException error(RedfishError kind) {
        return new RedfishRequestException(kind, "GET /x — " + kind, null);
    }

    /** 표준 자격 탐침의 응답을 스텁한다 — 이후 호출(반영 확인)은 verifyOutcome 으로 이어 스텁. */
    private void standardProbe(RedfishRequestException failure) {
        if (failure == null) {
            given(accountService.accounts(eq("10.0.0.9"), eq(standard))).willReturn(mapper.readTree("{}"));
        } else {
            given(accountService.accounts(eq("10.0.0.9"), eq(standard))).willThrow(failure);
        }
    }

    private void factoryProbe(RedfishRequestException failure) {
        BmcCredentials factory = new BmcCredentials("admin", SERIAL, "공장 기본(보드 시리얼)");
        if (failure == null) {
            given(accountService.accounts(eq("10.0.0.9"), eq(factory))).willReturn(mapper.readTree("{}"));
        } else {
            given(accountService.accounts(eq("10.0.0.9"), eq(factory))).willThrow(failure);
        }
    }

    private String recordedMeta(ProvisioningStatus expected) {
        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(any(), eq(ProvisioningPhaseStep.IPMI_SETTING), eq(expected),
                meta.capture(), any());
        return meta.getValue();
    }

    @Test
    @DisplayName("1행 — 표준 자격으로 열리면 no-op: 원장 무기록 · 변경 시도 없음 (멱등의 근거)")
    void alreadyStandard_noop() {
        standardProbe(null);

        service.standardize(SERVER_ID);

        verify(accountService, never()).changePassword(anyString(), any(), anyString(), anyString());
        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("2행 — 공장 기본으로 열림 → 신원 대조 → 교체 → 표준 자격 반영 확인 → SUCCEEDED 기록")
    void freshDevice_standardizes() {
        // 표준 탐침 401 → (교체 후) 반영 확인 성공 — 같은 인자의 연속 스텁.
        given(accountService.accounts(eq("10.0.0.9"), eq(standard)))
                .willThrow(error(RedfishError.AUTH_FAILED))
                .willReturn(mapper.readTree("{}"));
        factoryProbe(null);
        given(provider.verifyIdentity(any(), eq(SERIAL))).willReturn(BmcIdentity.MATCHED);

        service.standardize(SERVER_ID);

        verify(accountService).changePassword(eq("10.0.0.9"), any(), eq("admin"), eq("standard-pw"));
        String meta = recordedMeta(ProvisioningStatus.SUCCEEDED);
        assertThat(meta).contains("ACCOUNT_STANDARDIZED").doesNotContain("standard-pw").doesNotContain(SERIAL);
    }

    @Test
    @DisplayName("2행 변형 — 반영 확인이 거부되면 FAILED(VERIFY_FAILED): 어중간한 상태를 운영자에게 알린다")
    void verifyFails_recordsFailed() {
        given(accountService.accounts(eq("10.0.0.9"), eq(standard)))
                .willThrow(error(RedfishError.AUTH_FAILED))
                .willThrow(error(RedfishError.AUTH_FAILED));
        factoryProbe(null);
        given(provider.verifyIdentity(any(), eq(SERIAL))).willReturn(BmcIdentity.MATCHED);

        service.standardize(SERVER_ID);

        assertThat(recordedMeta(ProvisioningStatus.FAILED)).contains("VERIFY_FAILED");
    }

    @Test
    @DisplayName("3행 — 둘 다 거부 = 알 수 없는 자격: FAILED(UNKNOWN_CREDENTIALS) · 변경 시도 없음")
    void bothRejected_operatorNeeded() {
        standardProbe(error(RedfishError.AUTH_FAILED));
        factoryProbe(error(RedfishError.AUTH_FAILED));

        service.standardize(SERVER_ID);

        verify(accountService, never()).changePassword(anyString(), any(), anyString(), anyString());
        assertThat(recordedMeta(ProvisioningStatus.FAILED)).contains("UNKNOWN_CREDENTIALS");
    }

    @Test
    @DisplayName("4행 — 연결 불가는 일시 상태: 원장 무기록(다음 진단 재실행이 재시도)")
    void unreachable_silent() {
        standardProbe(error(RedfishError.CONNECT_FAILED));

        service.standardize(SERVER_ID);

        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("4행 변형 — 교체 PATCH 의 412(동시 사다리 경합)도 침묵: 다음 기회에 1행으로 수렴")
    void patchConflict_silent() {
        given(accountService.accounts(eq("10.0.0.9"), eq(standard))).willThrow(error(RedfishError.AUTH_FAILED));
        factoryProbe(null);
        given(provider.verifyIdentity(any(), eq(SERIAL))).willReturn(BmcIdentity.MATCHED);
        willThrow(error(RedfishError.PRECONDITION_FAILED))
                .given(accountService).changePassword(anyString(), any(), anyString(), anyString());

        service.standardize(SERVER_ID);

        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("D-5 — 신원 불일치는 사건: FAILED(IDENTITY_MISMATCHED) · 변경 시도 없음")
    void identityMismatch_stops() {
        standardProbe(error(RedfishError.AUTH_FAILED));
        factoryProbe(null);
        given(provider.verifyIdentity(any(), eq(SERIAL))).willReturn(BmcIdentity.MISMATCHED);

        service.standardize(SERVER_ID);

        verify(accountService, never()).changePassword(anyString(), any(), anyString(), anyString());
        assertThat(recordedMeta(ProvisioningStatus.FAILED)).contains("IDENTITY_MISMATCHED");
    }

    @Test
    @DisplayName("D-5 — 신원 확인 도달 불가는 상태: 침묵 · 변경 시도 없음")
    void identityUnreachable_silent() {
        standardProbe(error(RedfishError.AUTH_FAILED));
        factoryProbe(null);
        given(provider.verifyIdentity(any(), eq(SERIAL))).willReturn(BmcIdentity.UNREACHABLE);

        service.standardize(SERVER_ID);

        verify(accountService, never()).changePassword(anyString(), any(), anyString(), anyString());
        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("0행 — 표준 비밀번호 미설정이면 시도 자체를 생략한다")
    void standardNotConfigured_skips() {
        BmcAccountStandardizationService bare = new BmcAccountStandardizationService(
                serverRepository, detailRepository, new BmcCredentialsResolver("admin", ""),
                accountService, provider, recorder, mapper);

        bare.standardize(SERVER_ID);

        verify(accountService, never()).accounts(anyString(), any());
        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("보드 시리얼 미수집 — 공장 기본 후보가 없어 침묵 종료")
    void noSerial_skips() {
        GuestServerDetail detail = mock(GuestServerDetail.class);
        given(detail.getBmcIp()).willReturn(new IpAddressVO("10.0.0.9"));
        given(detail.getBoardSerial()).willReturn(null);
        given(detailRepository.findByServerIdWithBoardModel(SERVER_ID)).willReturn(Optional.of(detail));
        standardProbe(error(RedfishError.AUTH_FAILED));

        service.standardize(SERVER_ID);

        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("BMC IP 없음 · detail 없음 — 방어적 no-op (Redfish 호출 자체가 없다)")
    void noBmcIp_noop() {
        given(detailRepository.findByServerIdWithBoardModel(SERVER_ID)).willReturn(Optional.empty());

        service.standardize(SERVER_ID);

        verify(accountService, never()).accounts(anyString(), any());
    }
}
