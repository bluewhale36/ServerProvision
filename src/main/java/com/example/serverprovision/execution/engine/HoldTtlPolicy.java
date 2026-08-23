package com.example.serverprovision.execution.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 자원 결손 대기의 시한 정책(E2-1-b) — 값과 그 값을 쓰는 계산(만료 판정 · 잔여 시간)을 한곳에 둔다.
 * 게이트(만료 시 실패 전환)와 화면(잔여 시간 표시)이 같은 객체를 보므로 "화면이 남았다는데 실패가
 * 났다" 는 어긋남이 생기지 않는다.
 *
 * <p>기점은 대기 진입이 찍는 {@code lastTransitionAt} 이다 — 대기 중에는 다른 전이가 없어 기점이
 * 흔들리지 않는다(토론 D4). 값은 설정으로 주입하며 기본 48시간 — 자원 재등록이 영업일 하루를
 * 넘길 수 있되 무한 대기는 막는 절충이다.</p>
 */
@Component
public class HoldTtlPolicy {

    private final Duration ttl;

    public HoldTtlPolicy(@Value("${provision.execution.hold-ttl:48h}") Duration ttl) {
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    public boolean isExpired(LocalDateTime holdSince, LocalDateTime now) {
        return holdSince.plus(ttl).isBefore(now);
    }

    /** 시한까지 남은 분 — 이미 지났으면 0(화면이 음수를 그리지 않게). */
    public long remainingMinutes(LocalDateTime holdSince, LocalDateTime now) {
        long elapsed = Duration.between(holdSince, now).toMinutes();
        return Math.max(0L, ttl.toMinutes() - elapsed);
    }
}
