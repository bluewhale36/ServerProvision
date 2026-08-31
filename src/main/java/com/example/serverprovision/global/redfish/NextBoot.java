package com.example.serverprovision.global.redfish;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 다음 부팅 의도 (E2.5 D-2) — 전원을 움직이는 호출({@link RedfishPowerService})이 발행 직전에 무장한다.
 * wire 값 · 경로 · 되읽기 판정을 상수가 든다({@code BmcSettingItem} 과 같은 결).
 */
public enum NextBoot {

    /** 부트 순서대로 — 무장하지 않는다. 화면(운영자 단발 전원 제어) 경로의 값. */
    AS_CONFIGURED {
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
    PXE_ONCE {
        @Override
        BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials) {
            try {
                client.patchJsonRefreshingEtag(bmcIp, credentials, RedfishPowerService.SYSTEM_PATH,
                        RedfishPowerService.SYSTEM_PATH, OVERRIDE_BODY);
            } catch (RedfishRequestException e) {
                if (e.getError().resourceSpecific()) {
                    return BootOverrideOutcome.rejected(e.getMessage());
                }
                throw e;   // 연결 불가 · 자격증명 거부 — 다음 호출도 같은 이유로 실패하므로 기존 규칙(폴백 · FAILED)대로.
            }
            return readback(client, bmcIp, credentials);
        }

        /** 되읽기는 관찰이지 실패 판정이 아니다(D-4) — 불일치 · 리소스 단위 실패는 UNCONFIRMED 로 눕힌다. */
        private static BootOverrideOutcome readback(RedfishClient client, String bmcIp, BmcCredentials credentials) {
            JsonNode boot;
            try {
                boot = client.getJson(bmcIp, credentials, RedfishPowerService.SYSTEM_PATH).path("Boot");
            } catch (RedfishRequestException e) {
                if (e.getError().resourceSpecific()) {
                    return BootOverrideOutcome.unconfirmed();
                }
                throw e;
            }
            boolean applied = "Once".equals(boot.path("BootSourceOverrideEnabled").asString(null))
                    && "Pxe".equals(boot.path("BootSourceOverrideTarget").asString(null));
            return applied ? BootOverrideOutcome.applied() : BootOverrideOutcome.unconfirmed();
        }
    };

    static final Map<String, Object> OVERRIDE_BODY = Map.of("Boot", Map.of(
            "BootSourceOverrideEnabled", "Once",
            "BootSourceOverrideTarget", "Pxe",
            "BootSourceOverrideMode", "UEFI"));

    abstract BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials);
}
