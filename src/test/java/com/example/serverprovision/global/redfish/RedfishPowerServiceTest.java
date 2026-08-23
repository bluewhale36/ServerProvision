package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
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
        service = new RedfishPowerService(client, new BmcCredentialsResolver("admin", "standard-pw"), 0, 50);
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

        RedfishPowerService bare = new RedfishPowerService(client, new BmcCredentialsResolver("admin", ""), 0, 50);
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
        PowerControlResult result = service.powerOnAndVerify(TARGET);
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
        PowerControlResult fallback = service.powerOnAndVerify(TARGET);
        assertThat(fallback.kind()).isEqualTo(PowerControlResult.Kind.VERIFIED);
        assertThat(fallback.message()).contains("PowerCycle");
        assertThat(resets.get()).isEqualTo(2); // ON 1회 + PowerCycle 1회 — 폴백은 한 번만

        // 폴백 실패 — 끝까지 Off
        resets.set(0);
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Off"));
        PowerControlResult failed = service.powerOnAndVerify(TARGET);
        assertThat(failed.kind()).isEqualTo(PowerControlResult.Kind.FAILED);
        assertThat(failed.message()).contains("수동 개입");
        assertThat(resets.get()).isEqualTo(2);
    }
}
