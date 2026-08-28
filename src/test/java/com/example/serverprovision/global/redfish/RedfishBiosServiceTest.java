package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * E3-1 — BIOS 설정 호출의 계약 고정. PATCH 는 {@code If-Match: *} 를 먼저 쓰고 <b>412 일 때만</b> fresh ETag 로
 * 한 번 더(E0-4-3 실측 · MAAS 선례), pending 의 404 는 실패가 아니라 "비어 있음" 이다.
 */
@ExtendWith(MockitoExtension.class)
class RedfishBiosServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final RedfishTarget TARGET = new RedfishTarget("192.168.10.21", "QG260700082");
    private static final Map<String, Object> ATTRIBUTES = Map.of("BootMode", "UEFI");
    private static final Map<String, Object> BODY = Map.of("Attributes", ATTRIBUTES);

    @Mock RedfishClient client;

    private RedfishBiosService service;

    @BeforeEach
    void setUp() {
        var resolver = new BmcCredentialsResolver("admin", "standard-pw");
        service = new RedfishBiosService(client, new BmcCredentialsFallback(resolver, new BmcCredentialsMemory()));
    }

    @Test
    @DisplayName("bios — 현재값 전문과 ETag 를 그대로 돌려준다(readback · 412 폴백의 재료)")
    void bios_returnsResource() {
        RedfishResource resource = new RedfishResource(JSON.readTree("{\"Attributes\":{\"BootMode\":\"UEFI\"}}"), "W/\"7\"");
        given(client.getForResource(anyString(), any(), eq(RedfishBiosService.BIOS_PATH))).willReturn(resource);

        assertThat(service.bios(TARGET)).isSameAs(resource);
    }

    @Test
    @DisplayName("patchPending — If-Match:* 로 한 번에 받아들여지면 GET 을 하지 않는다")
    void patchPending_ifMatchAnyFirst() {
        service.patchPending(TARGET, ATTRIBUTES);

        verify(client).patchJson(eq(TARGET.bmcIp()), any(), eq(RedfishBiosService.PENDING_PATH), eq("*"), eq(BODY));
        verify(client, never()).getForResource(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("patchPending — 412 면 fresh ETag 를 받아 한 번 더 쓴다")
    void patchPending_412FallsBackToFreshEtag() {
        willThrow(new RedfishRequestException(RedfishError.PRECONDITION_FAILED, "412", null))
                .given(client).patchJson(anyString(), any(), anyString(), eq("*"), any());
        given(client.getForResource(anyString(), any(), eq(RedfishBiosService.BIOS_PATH)))
                .willReturn(new RedfishResource(JSON.readTree("{}"), "W/\"fresh\""));

        service.patchPending(TARGET, ATTRIBUTES);

        verify(client).patchJson(anyString(), any(), eq(RedfishBiosService.PENDING_PATH), eq("W/\"fresh\""), eq(BODY));
        verify(client, times(2)).patchJson(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("patchPending — 412 가 아닌 거절은 재시도 없이 그대로 올린다(호출자가 PATCH_REJECTED 로 닫는다)")
    void patchPending_otherErrorPropagatesWithoutRetry() {
        willThrow(new RedfishRequestException(RedfishError.PROTOCOL, "400 Bad Request", null))
                .given(client).patchJson(anyString(), any(), anyString(), anyString(), any());

        assertThatThrownBy(() -> service.patchPending(TARGET, ATTRIBUTES))
                .isInstanceOf(RedfishRequestException.class)
                .hasMessage("400 Bad Request");
        verify(client, times(1)).patchJson(anyString(), any(), anyString(), anyString(), any());
        verify(client, never()).getForResource(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("patchPending — fresh ETag 로도 412 면 두 번째 예외를 올린다(무한 재시도 없음)")
    void patchPending_412TwicePropagates() {
        willThrow(new RedfishRequestException(RedfishError.PRECONDITION_FAILED, "412", null))
                .given(client).patchJson(anyString(), any(), anyString(), anyString(), any());
        given(client.getForResource(anyString(), any(), anyString()))
                .willReturn(new RedfishResource(JSON.readTree("{}"), "W/\"fresh\""));

        assertThatThrownBy(() -> service.patchPending(TARGET, ATTRIBUTES))
                .isInstanceOf(RedfishRequestException.class)
                .extracting("error").isEqualTo(RedfishError.PRECONDITION_FAILED);
        verify(client, times(2)).patchJson(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("patchPending — 표준 계정이 401 이면 공장 기본(시리얼)으로 이어 쓴다(E1.5 P1 폴백 공유)")
    void patchPending_authFallback() {
        AtomicInteger calls = new AtomicInteger();
        willAnswer(inv -> {
            calls.incrementAndGet();
            BmcCredentials credentials = inv.getArgument(1);
            if ("standard-pw".equals(credentials.password())) {
                throw new RedfishRequestException(RedfishError.AUTH_FAILED, "401", null);
            }
            return null;
        }).given(client).patchJson(anyString(), any(), anyString(), anyString(), any());

        service.patchPending(TARGET, ATTRIBUTES);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("pending — 404 는 비어 있음(empty), 본문이 있으면 그대로")
    void pending_404IsEmptyOtherwiseBody() {
        given(client.getJson(anyString(), any(), eq(RedfishBiosService.PENDING_PATH)))
                .willThrow(new RedfishRequestException(RedfishError.NOT_FOUND, "404", null));
        assertThat(service.pending(TARGET)).isEmpty();

        JsonNode body = JSON.readTree("{\"Attributes\":{\"BootMode\":\"UEFI\"}}");
        given(client.getJson(anyString(), any(), eq(RedfishBiosService.PENDING_PATH))).willReturn(body);
        Optional<JsonNode> pending = service.pending(TARGET);
        assertThat(pending).isPresent();
        assertThat(pending.get().path("Attributes").path("BootMode").asString()).isEqualTo("UEFI");
    }

    @Test
    @DisplayName("pending — 404 가 아닌 실패는 그대로 올린다(비어 있음과 도달 불가를 섞지 않는다)")
    void pending_otherErrorPropagates() {
        given(client.getJson(anyString(), any(), anyString()))
                .willThrow(new RedfishRequestException(RedfishError.CONNECT_FAILED, "연결 불가", null));

        assertThatThrownBy(() -> service.pending(TARGET))
                .isInstanceOf(RedfishRequestException.class)
                .extracting("error").isEqualTo(RedfishError.CONNECT_FAILED);
    }

    @Test
    @DisplayName("registry — Bios.AttributeRegistry → Registries/{id} → Location[0].Uri 체인으로 전문을 받는다(경로 하드코딩 0, E3-3)")
    void registry_followsDiscoveryChain() {
        given(client.getForResource(anyString(), any(), eq(RedfishBiosService.BIOS_PATH))).willReturn(
                new RedfishResource(JSON.readTree("{\"AttributeRegistry\":\"BiosAttributeRegistry\"}"), "W/\"1\""));
        given(client.getJson(anyString(), any(), eq("/redfish/v1/Registries/BiosAttributeRegistry"))).willReturn(
                JSON.readTree("{\"Location\":[{\"Uri\":\"/redfish/v1/Registries/BiosAttributeRegistry.json\"}]}"));
        given(client.getJson(anyString(), any(), eq("/redfish/v1/Registries/BiosAttributeRegistry.json"))).willReturn(
                JSON.readTree("{\"RegistryEntries\":{\"Attributes\":[]}}"));

        RedfishRegistry registry = service.registry(TARGET);

        assertThat(registry.registryId()).isEqualTo("BiosAttributeRegistry");
        assertThat(registry.uri()).isEqualTo("/redfish/v1/Registries/BiosAttributeRegistry.json");
        assertThat(registry.rawJson()).contains("RegistryEntries");
    }

    @Test
    @DisplayName("registry — Location 이 없으면 PROTOCOL 로 올린다(호출자가 채집 불가로 다룬다)")
    void registry_missingLocationIsProtocolError() {
        given(client.getForResource(anyString(), any(), eq(RedfishBiosService.BIOS_PATH))).willReturn(
                new RedfishResource(JSON.readTree("{\"AttributeRegistry\":\"BiosAttributeRegistry\"}"), "W/\"1\""));
        given(client.getJson(anyString(), any(), eq("/redfish/v1/Registries/BiosAttributeRegistry"))).willReturn(
                JSON.readTree("{\"Location\":[]}"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.registry(TARGET))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.PROTOCOL));
    }
}
