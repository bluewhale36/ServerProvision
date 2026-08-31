package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * E1.5 CP4 — 전원 제어 판정 단위 테스트. mocked {@link RedfishClient} 로 네 결과(kind)와
 * 401 폴백 · PowerCycle 폴백 경로를 고정한다. 폴링은 간격 0 · 타임아웃 50ms 로 압축.
 */
@ExtendWith(MockitoExtension.class)
class RedfishPowerServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final RedfishTarget TARGET = new RedfishTarget("192.168.10.21", "QG260700082");

    @Mock RedfishClient client;

    private RedfishPowerService service;

    @BeforeEach
    void setUp() {
        var resolver = new BmcCredentialsResolver("admin", "standard-pw");
        service = new RedfishPowerService(client, resolver, new BmcCredentialsFallback(resolver, new BmcCredentialsMemory()), 0, 50);
    }

    private static tools.jackson.databind.JsonNode system(String powerState) {
        return JSON.readTree("{\"PowerState\":\"" + powerState + "\"}");
    }

    @Test
    @DisplayName("bmcIp null → UNSUPPORTED (호출 0) · 자격증명 후보 0 → FAILED")
    void unsupportedAndNoCredentials() {
        assertThat(service.powerState(new RedfishTarget(null, "QG260700082")).kind())
                .isEqualTo(PowerControlResult.Kind.UNSUPPORTED);
        verify(client, never()).getJson(anyString(), any(), anyString());

        var bareResolver = new BmcCredentialsResolver("admin", "");
        RedfishPowerService bare = new RedfishPowerService(client, bareResolver, new BmcCredentialsFallback(bareResolver, new BmcCredentialsMemory()), 0, 50);
        PowerControlResult result = bare.powerState(new RedfishTarget("192.168.10.21", null));
        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.FAILED);
        assertThat(result.message()).contains("자격증명");
    }

    @Test
    @DisplayName("powerState — SENT + RedfishPowerState 파싱 · 미지 문자열은 UNKNOWN")
    void powerState() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("On"));
        PowerControlResult result = service.powerState(TARGET);
        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.powerState()).isEqualTo(RedfishPowerState.ON);

        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Weird"));
        assertThat(service.powerState(TARGET).powerState()).isEqualTo(RedfishPowerState.UNKNOWN);
    }

    @Test
    @DisplayName("401 폴백 — 표준 계정 거부면 공장 기본(시리얼)으로 재시도해 성공한다(P1)")
    void authFallback() {
        given(client.getJson(anyString(), any(BmcCredentials.class), eq(RedfishPowerService.SYSTEM_PATH)))
                .willAnswer(invocation -> {
                    BmcCredentials credentials = invocation.getArgument(1);
                    if ("standard-pw".equals(credentials.password())) {
                        throw new RedfishRequestException(RedfishError.AUTH_FAILED, "401", null);
                    }
                    return system("Off");
                });
        PowerControlResult result = service.powerState(TARGET);
        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.powerState()).isEqualTo(RedfishPowerState.OFF);
    }

    @Test
    @DisplayName("연결 불가는 폴백하지 않고 즉시 FAILED — 자격증명과 무관한 실패라서")
    void connectFailedNoFallback() {
        AtomicInteger calls = new AtomicInteger();
        given(client.getJson(anyString(), any(), anyString())).willAnswer(inv -> {
            calls.incrementAndGet();
            throw new RedfishRequestException(RedfishError.CONNECT_FAILED, "연결 불가", null);
        });
        PowerControlResult result = service.powerState(TARGET);
        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.FAILED);
        assertThat(result.message()).contains("연결");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("reset — 단발: POST + Task 판독 + 직후 상태 1 회 → SENT (직후 조회 실패는 UNKNOWN 으로 눕힘)")
    void resetSingleShot() {
        given(client.postForTask(anyString(), any(), eq(RedfishPowerService.RESET_PATH), any()))
                .willReturn(Optional.of("/redfish/v1/TaskService/Tasks/1"));
        given(client.getJson(anyString(), any(), eq("/redfish/v1/TaskService/Tasks/1")))
                .willReturn(JSON.readTree("{\"TaskState\":\"Completed\"}"));
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Off"));

        PowerControlResult result = service.reset(TARGET, RedfishResetType.FORCE_OFF);
        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.powerState()).isEqualTo(RedfishPowerState.OFF);
        assertThat(result.message()).contains("강제 끄기").contains("[상태 조회]");
        verify(client).postForTask(anyString(), any(), eq(RedfishPowerService.RESET_PATH),
                eq(Map.of("ResetType", "ForceOff")));
    }

    @Test
    @DisplayName("powerOnAndVerify — 폴링으로 On 확인 → VERIFIED")
    void verifyHappy() {
        given(client.postForTask(anyString(), any(), anyString(), any())).willReturn(Optional.empty());
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("On"));
        PowerControlResult result = service.powerOnAndVerify(TARGET, NextBoot.AS_CONFIGURED);
        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.VERIFIED);
        assertThat(result.powerState()).isEqualTo(RedfishPowerState.ON);
    }

    @Test
    @DisplayName("powerOnAndVerify — 실측 실패 모드: On 후 불변 → PowerCycle 폴백 → 켜지면 VERIFIED, 끝내 불변이면 FAILED")
    void verifyFallbackAndFailure() {
        // 폴백 성공 — On 발행 후 Off 유지, PowerCycle 발행 이후 On
        AtomicInteger resets = new AtomicInteger();
        willAnswer(inv -> { resets.incrementAndGet(); return Optional.empty(); })
                .given(client).postForTask(anyString(), any(), eq(RedfishPowerService.RESET_PATH), any());
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH)))
                .willAnswer(inv -> resets.get() >= 2 ? system("On") : system("Off"));
        PowerControlResult fallback = service.powerOnAndVerify(TARGET, NextBoot.AS_CONFIGURED);
        assertThat(fallback.kind()).isEqualTo(PowerControlResult.Kind.VERIFIED);
        assertThat(fallback.message()).contains("PowerCycle");
        assertThat(resets.get()).isEqualTo(2); // ON 1회 + PowerCycle 1회 — 폴백은 한 번만

        // 폴백 실패 — 끝까지 Off
        resets.set(0);
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Off"));
        PowerControlResult failed = service.powerOnAndVerify(TARGET, NextBoot.AS_CONFIGURED);
        assertThat(failed.kind()).isEqualTo(PowerControlResult.Kind.FAILED);
        assertThat(failed.message()).contains("수동 개입");
        assertThat(resets.get()).isEqualTo(2);
    }

    // ---- E2.5 — 다음 부팅 무장 ------------------------------------------------

    @Test
    @DisplayName("PXE_ONCE — 무장 PATCH 가 Reset 보다 먼저 나가고, 되읽기 일치면 '반영 확인' 접두")
    void pxeOnce_armsBeforeResetApplied() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(JSON.readTree(
                "{\"PowerState\":\"On\",\"Boot\":{\"BootSourceOverrideEnabled\":\"Once\",\"BootSourceOverrideTarget\":\"Pxe\"}}"));
        given(client.postForTask(anyString(), any(), anyString(), any())).willReturn(Optional.empty());

        PowerControlResult result = service.reset(TARGET, RedfishResetType.FORCE_RESTART, NextBoot.PXE_ONCE);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.message()).contains("반영 확인");
        InOrder order = inOrder(client);
        order.verify(client).patchJsonRefreshingEtag(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH),
                eq(RedfishPowerService.SYSTEM_PATH), eq(NextBoot.OVERRIDE_BODY));
        order.verify(client).postForTask(anyString(), any(), eq(RedfishPowerService.RESET_PATH), any());
    }

    @Test
    @DisplayName("PXE_ONCE — 리소스 단위 거절은 best effort: Reset 은 나가고 메시지에 '거절' 접두(D-4)")
    void pxeOnce_rejectedStillResets() {
        willThrow(new RedfishRequestException(RedfishError.PROTOCOL, "PATCH /redfish/v1/Systems/Self — 거절(400)", null))
                .given(client).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Off"));
        given(client.postForTask(anyString(), any(), anyString(), any())).willReturn(Optional.empty());

        PowerControlResult result = service.reset(TARGET, RedfishResetType.ON, NextBoot.PXE_ONCE);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.message()).contains("거절").contains("부트 순서대로");
        verify(client).postForTask(anyString(), any(), eq(RedfishPowerService.RESET_PATH), any());
    }

    @Test
    @DisplayName("PXE_ONCE — 되읽기 불일치는 '미확인(pending 경유 가능)' 접두로 눕고 Reset 은 계속")
    void pxeOnce_unconfirmed() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(JSON.readTree(
                "{\"PowerState\":\"Off\",\"Boot\":{\"BootSourceOverrideEnabled\":\"Disabled\"}}"));
        given(client.postForTask(anyString(), any(), anyString(), any())).willReturn(Optional.empty());

        PowerControlResult result = service.reset(TARGET, RedfishResetType.ON, NextBoot.PXE_ONCE);

        assertThat(result.message()).contains("미확인");
    }

    @Test
    @DisplayName("PXE_ONCE — 무장 중 연결 불가는 FAILED 로 눕고 Reset 을 내지 않는다")
    void pxeOnce_connectFailureBlocksReset() {
        willThrow(new RedfishRequestException(RedfishError.CONNECT_FAILED, "연결 불가", null))
                .given(client).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());

        PowerControlResult result = service.reset(TARGET, RedfishResetType.ON, NextBoot.PXE_ONCE);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.FAILED);
        verify(client, never()).postForTask(anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("PXE_ONCE — 무장 401 은 다음 자격증명 후보로 폴백해 PATCH · Reset 을 그 후보로 잇는다(P1 공유)")
    void pxeOnce_authFallbackDuringArm() {
        willAnswer(inv -> {
            BmcCredentials credentials = inv.getArgument(1);
            if ("standard-pw".equals(credentials.password())) {
                throw new RedfishRequestException(RedfishError.AUTH_FAILED, "401", null);
            }
            return null;
        }).given(client).patchJsonRefreshingEtag(anyString(), any(BmcCredentials.class), anyString(), anyString(), any());
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(JSON.readTree(
                "{\"PowerState\":\"Off\",\"Boot\":{\"BootSourceOverrideEnabled\":\"Once\",\"BootSourceOverrideTarget\":\"Pxe\"}}"));
        given(client.postForTask(anyString(), any(), anyString(), any())).willReturn(Optional.empty());

        PowerControlResult result = service.reset(TARGET, RedfishResetType.ON, NextBoot.PXE_ONCE);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.message()).contains("반영 확인");
        verify(client, times(2)).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PXE_ONCE — powerOnAndVerify 는 PowerCycle 폴백 직전 다시 무장한다(Once 소진 대비, D-5)")
    void pxeOnce_reArmsBeforePowerCycleFallback() {
        AtomicInteger resets = new AtomicInteger();
        willAnswer(inv -> { resets.incrementAndGet(); return Optional.empty(); })
                .given(client).postForTask(anyString(), any(), eq(RedfishPowerService.RESET_PATH), any());
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH)))
                .willAnswer(inv -> resets.get() >= 2 ? system("On") : system("Off"));

        service.powerOnAndVerify(TARGET, NextBoot.PXE_ONCE);

        verify(client, times(2)).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("AS_CONFIGURED — 화면 경로는 PATCH 0건 · 문구 그대로(D-6 · D-9)")
    void asConfigured_noPatch() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Off"));
        given(client.postForTask(anyString(), any(), anyString(), any())).willReturn(Optional.empty());

        PowerControlResult result = service.reset(TARGET, RedfishResetType.FORCE_OFF);

        assertThat(result.message()).startsWith("강제 끄기");
        verify(client, never()).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
    }
}
