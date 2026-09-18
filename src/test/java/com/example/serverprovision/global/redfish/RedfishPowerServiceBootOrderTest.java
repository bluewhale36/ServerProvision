package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HF20 CP4 — 부트 순서 정착의 서비스 경계. ① settleBootOrder 의 적용 · 무변경 · 실패 ② 무장 경로가 오버라이드 PATCH
 * 앞에 SHELL_LAST 를 끼우는지(순서) ③ AS_CONFIGURED 는 정착을 거치지 않는지 ④ 정착 실패가 무장을 막지 않는지(best effort).
 */
@ExtendWith(MockitoExtension.class)
class RedfishPowerServiceBootOrderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final RedfishTarget TARGET = new RedfishTarget("192.168.10.21", "QG260700082");
    private static final Map<String, Object> SHELL_LAST_BODY =
            Map.of("Boot", Map.of("BootOrder", List.of("Boot0003", "Boot0002", "Boot0001")));

    @Mock RedfishClient client;

    private RedfishPowerService service;

    @BeforeEach
    void setUp() {
        var resolver = new BmcCredentialsResolver("admin", "standard-pw");
        service = new RedfishPowerService(client, resolver, new BmcCredentialsFallback(resolver, new BmcCredentialsMemory()), 0, 50);
    }

    /** 2호기 실측 모양 — Windows 설치 직후 결함 상태(셸 첫 항목). */
    private static JsonNode system(String... order) {
        return JSON.readTree("{\"PowerState\":\"On\",\"Boot\":{\"BootSourceOverrideEnabled\":\"Once\",\"BootSourceOverrideTarget\":\"Pxe\","
                + "\"BootOrder\":[" + String.join(",", java.util.Arrays.stream(order).map(o -> "\"" + o + "\"").toList()) + "]}}");
    }

    private void bmcAnswers(String... order) {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system(order));
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.BOOT_OPTIONS_PATH)))
                .willReturn(JSON.readTree(BootEntriesTest.OPTIONS));
    }

    @Test
    @DisplayName("settleBootOrder(SHELL_LAST) — 셸이 첫 항목이면 BootOrder PATCH(If-Match 사다리 경로) → SENT · 메시지에 새 순서")
    void settleApplies() {
        bmcAnswers("Boot0001", "Boot0003", "Boot0002");

        PowerControlResult result = service.settleBootOrder(TARGET, BootOrderPolicy.SHELL_LAST);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.message()).contains("셸을 맨 뒤로").contains("Boot0003,Boot0002,Boot0001");
        verify(client).patchJsonRefreshingEtag(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH),
                eq(RedfishPowerService.SYSTEM_PATH), eq(SHELL_LAST_BODY));
    }

    @Test
    @DisplayName("settleBootOrder(DISK_FIRST) — 이미 그 순서면 PATCH 0 · SENT '손대지 않았습니다'(idempotent)")
    void settleUnchanged() {
        bmcAnswers("Boot0002", "Boot0004", "Boot0003", "Boot0001");

        PowerControlResult result = service.settleBootOrder(TARGET, BootOrderPolicy.DISK_FIRST);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        assertThat(result.message()).contains("손대지 않았습니다");
        verify(client, never()).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("settleBootOrder — BootOptions 를 못 읽으면(미지원 BMC · 404) FAILED 로 사유 · 예외는 새지 않는다")
    void settleFailsSoftly() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Boot0001", "Boot0002"));
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.BOOT_OPTIONS_PATH)))
                .willThrow(new RedfishRequestException(RedfishError.NOT_FOUND, "GET BootOptions — 404", null));

        PowerControlResult result = service.settleBootOrder(TARGET, BootOrderPolicy.DISK_FIRST);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.FAILED);
        assertThat(result.message()).contains("정착하지 못했습니다").contains("404");
        verify(client, never()).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("settleBootOrder — 401 은 삼키지 않는다 : 표준 계정 거부 → 공장 기본(시리얼)으로 다시 읽어 정착한다(폴백 사다리)")
    void settleFallsBackOnAuthFailure() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willAnswer(inv -> {
            BmcCredentials c = inv.getArgument(1);
            if ("standard-pw".equals(c.password())) {
                throw new RedfishRequestException(RedfishError.AUTH_FAILED, "GET Systems/Self — 401", null);
            }
            return system("Boot0001", "Boot0002");
        });
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.BOOT_OPTIONS_PATH)))
                .willReturn(JSON.readTree(BootEntriesTest.OPTIONS));

        PowerControlResult result = service.settleBootOrder(TARGET, BootOrderPolicy.SHELL_LAST);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        verify(client).patchJsonRefreshingEtag(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH),
                eq(RedfishPowerService.SYSTEM_PATH), eq(Map.of("Boot", Map.of("BootOrder", List.of("Boot0002", "Boot0001")))));
    }

    @Test
    @DisplayName("armBootOverride(PXE_ONCE) — SHELL_LAST PATCH 가 Once 본문 PATCH 보다 먼저 나간다 (POST 가 셸을 1순위로 올릴 길을 막는다)")
    void armSettlesShellLastFirst() {
        bmcAnswers("Boot0001", "Boot0003", "Boot0002");

        PowerControlResult result = service.armBootOverride(TARGET, NextBoot.PXE_ONCE);

        assertThat(result.kind()).isEqualTo(PowerControlResult.Kind.SENT);
        InOrder order = inOrder(client);
        order.verify(client).patchJsonRefreshingEtag(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH),
                eq(RedfishPowerService.SYSTEM_PATH), eq(SHELL_LAST_BODY));
        order.verify(client).patchJsonRefreshingEtag(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH),
                eq(RedfishPowerService.SYSTEM_PATH), eq(NextBoot.OVERRIDE_BODY));
    }

    @Test
    @DisplayName("reset(FORCE_RESTART, PXE_ONCE) — 정착 → 무장 → Reset 순서 · 정착이 실패해도(BootOptions 404) 무장과 Reset 은 나간다")
    void resetContinuesWhenSettlementFails() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Boot0001", "Boot0002"));
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.BOOT_OPTIONS_PATH)))
                .willThrow(new RedfishRequestException(RedfishError.NOT_FOUND, "GET BootOptions — 404", null));

        PowerControlResult result = service.reset(TARGET, RedfishResetType.FORCE_RESTART, NextBoot.PXE_ONCE);

        assertThat(result.kind()).isIn(PowerControlResult.Kind.SENT, PowerControlResult.Kind.VERIFIED);
        InOrder order = inOrder(client);
        order.verify(client).patchJsonRefreshingEtag(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH),
                eq(RedfishPowerService.SYSTEM_PATH), eq(NextBoot.OVERRIDE_BODY));
        order.verify(client).postForTask(anyString(), any(), eq(RedfishPowerService.RESET_PATH), any());
        verify(client, never()).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), eq(SHELL_LAST_BODY));
    }

    @Test
    @DisplayName("AS_CONFIGURED(무장 없음) — BootOptions 를 읽지도, BootOrder 를 만지지도 않는다")
    void asConfiguredSkipsSettlement() {
        given(client.getJson(anyString(), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(system("Boot0001", "Boot0002"));

        service.reset(TARGET, RedfishResetType.ON, NextBoot.AS_CONFIGURED);

        verify(client, never()).getJson(anyString(), any(), eq(RedfishPowerService.BOOT_OPTIONS_PATH));
        verify(client, never()).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
    }
}
