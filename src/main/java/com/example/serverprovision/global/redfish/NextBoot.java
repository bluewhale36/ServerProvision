package com.example.serverprovision.global.redfish;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 다음 부팅 의도 (E2.5 D-2 · HF15-1 확장) — 전원을 움직이는 호출({@link RedfishPowerService})이 발행 직전에 무장하고,
 * PXE 보장 조정자({@code BootOverrideReconciler})가 진행 상태에 맞춰 세우거나 푼다. wire 값 · 경로 · 되읽기 판정을
 * 상수가 든다({@code BmcSettingItem} 과 같은 결).
 *
 * <p>실기 3호(2026-09-08)에서 {@code Once} 는 두 경로에서 깨졌다 — BIOS 설정 적용의 내부 재시작이 한 번을 소비했고(F-2),
 * 진단 리눅스 에이전트의 자기 재부팅에는 무장 자체가 없었다(F-2b). 그래서 프로비저닝 중에는 {@link #PXE_CONTINUOUS} 로
 * 두고 종단 · 실패 · 회수 · 설치 착수 때 {@link #RELEASE} 로 푼다.</p>
 */
public enum NextBoot {

    /** 부트 순서대로 — 무장하지 않는다. 화면(운영자 단발 전원 제어) 경로의 값. */
    AS_CONFIGURED("") {
        @Override
        BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials) {
            return BootOverrideOutcome.none();
        }
    },

    /**
     * 다음 한 번은 PXE — {@code Boot{Once · Pxe · UEFI}} 를 {@code Systems/Self} 에 직접 PATCH 한다(D-3).
     * Mode 를 함께 보내는 이유: 실측(E0-4-1) 현재값이 Legacy 인데 이 플랫폼의 부트 옵션과 PXE 사슬(ipxe.efi)은
     * 전부 UEFI 라, Target 만 바꾸면 Legacy PXE 를 찾다 부트 순서로 폴스루할 수 있다. Once 는 POST 1회에 소진된다.
     */
    PXE_ONCE("다음 부팅 PXE 강제") {
        @Override
        BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials) {
            return patchAndReadback(client, bmcIp, credentials, overrideBody("Once"), "Once", true);
        }
    },

    /** 풀 때까지 매 부팅 PXE(HF15-1) — 프로비저닝 창 안의 모든 재부팅 경로를 한 장치로 덮는다. */
    PXE_CONTINUOUS("프로비저닝 중 PXE 보장") {
        @Override
        BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials) {
            return patchAndReadback(client, bmcIp, credentials, overrideBody("Continuous"), "Continuous", true);
        }
    },

    /** 보장 해제(HF15-1) — {@code BootSourceOverrideEnabled=Disabled}. 부트 순서대로 돌아간다. */
    RELEASE("PXE 보장 해제") {
        @Override
        BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials) {
            return patchAndReadback(client, bmcIp, credentials,
                    Map.of("Boot", Map.of("BootSourceOverrideEnabled", "Disabled")), "Disabled", false);
        }
    };

    private final String label;

    NextBoot(String label) {
        this.label = label;
    }

    /** 결과 메시지 · 로그의 표기 — 무장 종류마다 다르다(Once · Continuous · 해제). */
    public String label() {
        return label;
    }

    static Map<String, Object> overrideBody(String enabled) {
        return Map.of("Boot", Map.of(
                "BootSourceOverrideEnabled", enabled,
                "BootSourceOverrideTarget", "Pxe",
                "BootSourceOverrideMode", "UEFI"));
    }

    /** 실측 호환 유지(E2.5 테스트 계약) — Once 본문. */
    static final Map<String, Object> OVERRIDE_BODY = overrideBody("Once");

    abstract BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials);

    /** PATCH → 되읽기. 되읽기는 관찰이지 실패 판정이 아니다(D-4) — 불일치 · 리소스 단위 실패는 UNCONFIRMED 로 눕힌다. */
    private static BootOverrideOutcome patchAndReadback(RedfishClient client, String bmcIp, BmcCredentials credentials,
                                                        Map<String, Object> body, String expectedEnabled, boolean expectPxe) {
        try {
            client.patchJsonRefreshingEtag(bmcIp, credentials, RedfishPowerService.SYSTEM_PATH,
                    RedfishPowerService.SYSTEM_PATH, body);
        } catch (RedfishRequestException e) {
            if (e.getError().resourceSpecific()) {
                return BootOverrideOutcome.rejected(e.getMessage());
            }
            throw e;   // 연결 불가 · 자격증명 거부 — 다음 호출도 같은 이유로 실패하므로 기존 규칙(폴백 · FAILED)대로.
        }
        JsonNode boot;
        try {
            boot = client.getJson(bmcIp, credentials, RedfishPowerService.SYSTEM_PATH).path("Boot");
        } catch (RedfishRequestException e) {
            if (e.getError().resourceSpecific()) {
                return BootOverrideOutcome.unconfirmed();
            }
            throw e;
        }
        boolean applied = expectedEnabled.equals(boot.path("BootSourceOverrideEnabled").asString(null))
                && (!expectPxe || "Pxe".equals(boot.path("BootSourceOverrideTarget").asString(null)));
        return applied ? BootOverrideOutcome.applied() : BootOverrideOutcome.unconfirmed();
    }
}
