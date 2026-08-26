package com.example.serverprovision.execution.engine.setting;

/** 항목 하나의 결과(E3-2 D-9) — 원장 meta 의 {@code items.<NAME>} 값. */
public record BmcItemOutcome(Status status, String detail) {

    public enum Status {
        /** 썼고 되읽어 확인했다. */
        APPLIED,
        /** 적용할 재료가 없거나 설정으로 꺼 두어 건너뛰었다 — 실패가 아니다. */
        SKIPPED,
        /** BMC 가 쓰기를 거절했다(데이터 · 프로토콜). */
        REJECTED,
        /** 썼는데 되읽은 값이 다르다. */
        MISMATCH,
        /** 썼는데 되읽기 전에 연결이 끊겼다 — Bond 재구성. 다음 주기가 거둔다. */
        RECONNECT_PENDING
    }

    public static BmcItemOutcome applied() {
        return new BmcItemOutcome(Status.APPLIED, "");
    }

    public static BmcItemOutcome skipped(String reason) {
        return new BmcItemOutcome(Status.SKIPPED, reason);
    }

    public static BmcItemOutcome rejected(String detail) {
        return new BmcItemOutcome(Status.REJECTED, detail);
    }

    public static BmcItemOutcome mismatch(String detail) {
        return new BmcItemOutcome(Status.MISMATCH, detail);
    }

    public static BmcItemOutcome reconnectPending() {
        return new BmcItemOutcome(Status.RECONNECT_PENDING, "Bond 적용 뒤 재접속 대기");
    }

    /** 원장 meta 표기 — 상태만, 또는 "상태:상세". */
    public String wire() {
        return detail == null || detail.isBlank() ? status.name() : status.name() + ":" + detail;
    }
}
