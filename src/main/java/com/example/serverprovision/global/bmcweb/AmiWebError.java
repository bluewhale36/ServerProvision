package com.example.serverprovision.global.bmcweb;

/**
 * AMI 웹 API 호출 실패의 분류(E3-2 D-4) — 실측 두 모양을 그대로 옮겼다: 인증 실패는 HTTP 상태가 아니라 바디
 * {@code {"cc":7,"error":"Invalid Authentication"}} 이고, 데이터 거절은 {@code {"error":"Invalid Data","code":1010}} 이다.
 */
public enum AmiWebError {
    /** 연결 자체가 안 됨 — Bond 재구성 직후 · BMC 재기동 · 경로 불가. */
    CONNECT_FAILED,
    /** 바디 {@code cc:7}(또는 401) — 세션 만료 · 자격증명 거부. 재로그인 · 다음 후보의 신호. */
    AUTH_FAILED,
    /** 바디 {@code error + code} — BMC 가 요청 내용을 거절(예: 1010 Invalid Data). */
    DATA_REJECTED,
    /** 그 외 — 비 200 · 해석 불가 바디 · 알 수 없는 cc. */
    PROTOCOL
}
