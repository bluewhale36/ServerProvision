package com.example.serverprovision.global.redfish;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 다음 부팅 의도 (E2.5 D-2 · HF17 개정) — 전원을 움직이는 호출({@link RedfishPowerService})이 발행 직전에 무장하고,
 * 에이전트에게 REBOOT 를 지시하는 자리({@code RebootDirectiveArmer})도 같은 값을 세운다. wire 값 · 경로 · 되읽기 판정을
 * 상수가 든다({@code BmcSettingItem} 과 같은 결).
 *
 * <p>HF17(2026-09-16 실측) — {@code Continuous} 는 AMI BIOS 가 persistent device 로 <b>기억</b>해 매 POST 부트 순서를
 * 덧씌우고, 그 기억은 Redfish 어휘(Disabled · Once · None)로 지울 수 없다(CMOS 클리어만). HF15-1 이 세우던 Continuous 와
 * 그 해제(Disabled)는 그래서 없앴다. {@link #PXE_ONCE} 는 그 POST 만 덧씌우고 기억을 만들지 않는다 — Continuous 가 덮었던
 * 두 구멍(설정 적용의 내부 재시작이 Once 를 소비 · 에이전트 자기 reboot 에 무장 없음)은 각자 자리에서 막는다
 * (미도착 재무장 · REBOOT 지시 직전 무장).</p>
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
            return patchAndReadback(client, bmcIp, credentials, "Pxe", OVERRIDE_BODY);
        }
    },

    /**
     * 다음 한 번은 디스크(HF20 G-1) — Windows Setup 이 자기 부팅 항목을 만들고 스스로 재부팅하는 POST 에서 이 BIOS 는
     * 항목 목록을 셸 우선으로 다시 세운다. 그 재부팅은 BMC 를 거치지 않으므로, wimboot 체인을 서빙한 직후 미리 세워 둔다.
     * 오버라이드로 디스크에서 부팅하면 맞바꿈 규칙이 Hard Disk 를 Fixed Boot Order 1순위로 올려 이후 재부팅이 안정된다.
     */
    HDD_ONCE("다음 부팅 디스크 강제") {
        @Override
        BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials) {
            return patchAndReadback(client, bmcIp, credentials, "Hdd", HDD_OVERRIDE_BODY);
        }
    };

    private final String label;

    NextBoot(String label) {
        this.label = label;
    }

    /** 결과 메시지 · 로그의 표기. */
    public String label() {
        return label;
    }

    /** Once 본문(E2.5 테스트 계약) — PXE. Mode 를 함께 보내는 이유는 {@link #PXE_ONCE} 참고. */
    static final Map<String, Object> OVERRIDE_BODY = overrideBody("Pxe");
    /** Once 본문 — 디스크(HF20 G-1). */
    static final Map<String, Object> HDD_OVERRIDE_BODY = overrideBody("Hdd");

    private static Map<String, Object> overrideBody(String target) {
        return Map.of("Boot", Map.of(
                "BootSourceOverrideEnabled", "Once",
                "BootSourceOverrideTarget", target,
                "BootSourceOverrideMode", "UEFI"));
    }

    abstract BootOverrideOutcome arm(RedfishClient client, String bmcIp, BmcCredentials credentials);

    /** 오버라이드를 실제로 세우는가 — 세우는 값만 부트 순서 정착(HF20 · 셸 맨 뒤)을 먼저 지난다. */
    public boolean armsOverride() {
        return this != AS_CONFIGURED;
    }

    /** PATCH → 되읽기. 되읽기는 관찰이지 실패 판정이 아니다(D-4) — 불일치 · 리소스 단위 실패는 UNCONFIRMED 로 눕힌다. */
    private static BootOverrideOutcome patchAndReadback(RedfishClient client, String bmcIp, BmcCredentials credentials,
                                                        String target, Map<String, Object> body) {
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
        boolean applied = "Once".equals(boot.path("BootSourceOverrideEnabled").asString(null))
                && target.equals(boot.path("BootSourceOverrideTarget").asString(null));
        return applied ? BootOverrideOutcome.applied() : BootOverrideOutcome.unconfirmed();
    }
}
