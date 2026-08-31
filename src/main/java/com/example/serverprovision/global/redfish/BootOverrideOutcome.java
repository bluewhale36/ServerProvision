package com.example.serverprovision.global.redfish;

/**
 * 다음 부팅 강제(무장)의 관찰 결과 (E2.5 D-4) — 실패 판정이 아니라 관찰이다. REJECTED 여도 전원 명령은
 * 계속되며(best effort), 이 요약이 결과 메시지 · 로그에 남아 실기에서 적용 경로(직접 vs pending)를 판독하게 한다.
 */
public record BootOverrideOutcome(Status status, String detail) {

    public enum Status {
        /** 무장 자체가 없었다({@link NextBoot#AS_CONFIGURED}). */
        NONE,
        /** PATCH 수락 + 되읽기에서 Once · Pxe 확인. */
        APPLIED,
        /** PATCH 는 2xx 인데 되읽기가 불일치 — pending(SD) 경유 가능성(K1-d 미실측). */
        UNCONFIRMED,
        /** 리소스 단위 거절(400 · 404 · 412 재시도 실패 등) — 게스트가 부트 순서대로 부팅될 수 있다. */
        REJECTED
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

    /** 결과 메시지 접두 — NONE 은 빈 문자열(화면 경로의 문구는 바뀌지 않는다, D-9). */
    public String prefix() {
        return switch (status) {
            case NONE -> "";
            case APPLIED -> "다음 부팅 PXE 강제 : 반영 확인 · ";
            case UNCONFIRMED -> "다음 부팅 PXE 강제 : 미확인(pending 경유 가능) · ";
            case REJECTED -> "다음 부팅 PXE 강제 : 거절(" + detail + ") — 부트 순서대로 부팅될 수 있습니다 · ";
        };
    }
}
