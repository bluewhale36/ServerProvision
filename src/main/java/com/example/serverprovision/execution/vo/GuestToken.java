package com.example.serverprovision.execution.vo;

import java.util.UUID;

/**
 * 게스트 신원 토큰(E1-0b, DEC-5) — 부팅 응답의 커널 인자로 게스트에 전달되고, 에이전트 API 의
 * 모든 요청에서 대조된다. 내부 프로비저닝망 전제 위의 사칭 보고 차단 안전망이며 회전은 없다(고정).
 */
public record GuestToken(String value) {

    public GuestToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("게스트 토큰 값이 비어 있습니다.");
        }
    }

    /** 등록 트랜잭션에서 1회 발급 — UUID 랜덤의 대시 제거 32자. */
    public static GuestToken issue() {
        return new GuestToken(UUID.randomUUID().toString().replace("-", ""));
    }

    /** 에이전트가 제시한 문자열과의 대조 단일 지점. */
    public boolean matches(String presented) {
        return value.equals(presented);
    }
}
