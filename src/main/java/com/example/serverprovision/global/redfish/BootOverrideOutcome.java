package com.example.serverprovision.global.redfish;

import com.example.serverprovision.global.ui.enums.FlashTone;

/**
 * 다음 부팅 강제(무장)의 관찰 결과 (E2.5 D-4) — 실패 판정이 아니라 관찰이다. REJECTED 여도 전원 명령은
 * 계속되며(best effort), 이 요약이 결과 메시지 · 로그에 남아 실기에서 적용 경로(직접 vs pending)를 판독하게 한다.
 */
public record BootOverrideOutcome(Status status, String detail) {

    /**
     * 관찰 상태. 상수마다 [재시도 후 네트워크 부팅] 알림의 문장과 색을 가진다(S22-3) — 전원 명령은 전달됐다는
     * 전제의 문장이고, 전원 제어 자체가 실패한 경우는 {@link PowerControlResult#retryBootNotice()} 가 먼저 말한다.
     */
    public enum Status {
        /** 무장 자체가 없었다({@link NextBoot#AS_CONFIGURED}). */
        NONE("재시도를 접수했습니다.", FlashTone.SUCCESS),
        /** PATCH 수락 + 되읽기에서 Once · Pxe 확인. */
        APPLIED("재시도를 접수했습니다. 서버를 네트워크 부팅으로 다시 시작했습니다.", FlashTone.SUCCESS),
        /** PATCH 는 2xx 인데 되읽기가 불일치 — pending(SD) 경유 가능성(K1-d 미실측). */
        UNCONFIRMED("재시도를 접수했지만 네트워크 부팅 지정을 확인하지 못했습니다. 서버가 디스크로 부팅하면 다시 시도해야 합니다.", FlashTone.WARNING),
        /** 리소스 단위 거절(400 · 404 · 412 재시도 실패 등) — 게스트가 부트 순서대로 부팅될 수 있다. */
        REJECTED("재시도를 접수했지만 BMC 가 네트워크 부팅 지정을 거절했습니다(%s). 서버가 디스크로 부팅할 수 있습니다.", FlashTone.WARNING);

        private final String retryNotice;
        private final FlashTone retryTone;

        Status(String retryNotice, FlashTone retryTone) {
            this.retryNotice = retryNotice;
            this.retryTone = retryTone;
        }
    }

    /** [재시도 후 네트워크 부팅] 알림 문장 — 거절이면 BMC 가 밝힌 사유를 괄호에 넣는다. */
    public String retryNotice() {
        return status.retryNotice.formatted(detail);
    }

    public FlashTone retryTone() {
        return status.retryTone;
    }

    public static BootOverrideOutcome none() {
        return new BootOverrideOutcome(Status.NONE, null);
    }

    public static BootOverrideOutcome applied() {
        return new BootOverrideOutcome(Status.APPLIED, null);
    }

    public static BootOverrideOutcome unconfirmed() {
        return new BootOverrideOutcome(Status.UNCONFIRMED, null);
    }

    public static BootOverrideOutcome rejected(String detail) {
        return new BootOverrideOutcome(Status.REJECTED, detail);
    }

    /** 결과 메시지 접두 — NONE 은 빈 문자열(화면 경로의 문구는 바뀌지 않는다, D-9). E2.5 계약 유지(Once 표기). */
    public String prefix() {
        return prefix(NextBoot.PXE_ONCE.label());
    }

    /** 무장 종류별 표기(HF15-1) — {@code label} 은 {@link NextBoot#label()}. */
    public String prefix(String label) {
        return switch (status) {
            case NONE -> "";
            case APPLIED -> label + " : 반영 확인 · ";
            case UNCONFIRMED -> label + " : 미확인(pending 경유 가능) · ";
            case REJECTED -> label + " : 거절(" + detail + ") — 부트 순서대로 부팅될 수 있습니다 · ";
        };
    }
}
