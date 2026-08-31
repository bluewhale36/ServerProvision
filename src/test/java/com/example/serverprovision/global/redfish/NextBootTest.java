package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * E2.5 D-2 · D-4 — 무장 상수의 wire 계약과 관찰 판정. 경로 · 바디 · 되읽기 규칙의
 * SSOT(Single Source of Truth)가 상수임을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class NextBootTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BmcCredentials CREDS = new BmcCredentials("admin", "pw", "표준 계정");
    private static final String BMC_IP = "10.10.0.51";

    @Mock RedfishClient client;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("PXE_ONCE 바디 — Once · Pxe · UEFI 셋(Mode 는 실측 현재값 Legacy 를 덮는 명시, E0-4-1)")
    void overrideBodyWireValues() {
        assertThat(NextBoot.OVERRIDE_BODY).containsOnlyKeys("Boot");
        assertThat((Map<String, Object>) NextBoot.OVERRIDE_BODY.get("Boot"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "BootSourceOverrideEnabled", "Once",
                        "BootSourceOverrideTarget", "Pxe",
                        "BootSourceOverrideMode", "UEFI"));
    }

    @Test
    @DisplayName("AS_CONFIGURED — 호출 0 · NONE · 접두 없음(화면 경로 무변경)")
    void asConfiguredDoesNothing() {
        BootOverrideOutcome outcome = NextBoot.AS_CONFIGURED.arm(client, BMC_IP, CREDS);

        assertThat(outcome.status()).isEqualTo(BootOverrideOutcome.Status.NONE);
        assertThat(outcome.prefix()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("PXE_ONCE — Systems/Self 에 사다리 PATCH 후 되읽기 일치면 APPLIED")
    void pxeOnceApplied() {
        given(client.getJson(eq(BMC_IP), any(), eq(RedfishPowerService.SYSTEM_PATH))).willReturn(
                JSON.readTree("{\"Boot\":{\"BootSourceOverrideEnabled\":\"Once\",\"BootSourceOverrideTarget\":\"Pxe\"}}"));

        BootOverrideOutcome outcome = NextBoot.PXE_ONCE.arm(client, BMC_IP, CREDS);

        assertThat(outcome.status()).isEqualTo(BootOverrideOutcome.Status.APPLIED);
        verify(client).patchJsonRefreshingEtag(eq(BMC_IP), any(),
                eq(RedfishPowerService.SYSTEM_PATH), eq(RedfishPowerService.SYSTEM_PATH), eq(NextBoot.OVERRIDE_BODY));
    }

    @Test
    @DisplayName("PXE_ONCE — 되읽기 불일치(pending 경유 가능) · 되읽기의 리소스 단위 실패는 UNCONFIRMED")
    void pxeOnceUnconfirmed() {
        given(client.getJson(eq(BMC_IP), any(), anyString()))
                .willReturn(JSON.readTree("{\"Boot\":{\"BootSourceOverrideEnabled\":\"Disabled\"}}"));
        assertThat(NextBoot.PXE_ONCE.arm(client, BMC_IP, CREDS).status())
                .isEqualTo(BootOverrideOutcome.Status.UNCONFIRMED);

        willThrow(new RedfishRequestException(RedfishError.PROTOCOL, "이상 응답", null))
                .given(client).getJson(eq(BMC_IP), any(), anyString());
        assertThat(NextBoot.PXE_ONCE.arm(client, BMC_IP, CREDS).status())
                .isEqualTo(BootOverrideOutcome.Status.UNCONFIRMED);
    }

    @Test
    @DisplayName("PXE_ONCE — 리소스 단위 거절은 REJECTED(사유 보존 · 되읽기 없음), 연결 불가는 그대로 올린다")
    void pxeOnceRejectionAndPropagation() {
        willThrow(new RedfishRequestException(RedfishError.PROTOCOL, "PATCH — 거절(400)", null))
                .given(client).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
        BootOverrideOutcome outcome = NextBoot.PXE_ONCE.arm(client, BMC_IP, CREDS);
        assertThat(outcome.status()).isEqualTo(BootOverrideOutcome.Status.REJECTED);
        assertThat(outcome.detail()).contains("거절(400)");
        assertThat(outcome.prefix()).contains("부트 순서대로");
        verify(client, never()).getJson(anyString(), any(), anyString());

        willThrow(new RedfishRequestException(RedfishError.CONNECT_FAILED, "연결 불가", null))
                .given(client).patchJsonRefreshingEtag(anyString(), any(), anyString(), anyString(), any());
        assertThatThrownBy(() -> NextBoot.PXE_ONCE.arm(client, BMC_IP, CREDS))
                .isInstanceOfSatisfying(RedfishRequestException.class,
                        e -> assertThat(e.getError()).isEqualTo(RedfishError.CONNECT_FAILED));
    }
}
