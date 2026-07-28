package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dhcpd.leases 파싱 스냅샷 — lease 블록 전량. 활성 건수 등 집계를 이 위에서 순수 계산한다.
 */
public record LeaseSnapshot(List<LeaseEntry> entries) {

    public static LeaseSnapshot empty() {
        return new LeaseSnapshot(List.of());
    }

    /** 활성 임대 건수 — IP 기준 최신 블록 dedup(ISC 는 append, 마지막 블록이 최신) + state==ACTIVE && ends>now. */
    public int activeCount(Instant now) {
        Map<String, LeaseEntry> latest = new LinkedHashMap<>();
        for (LeaseEntry e : entries) {
            latest.put(e.ip().value(), e);   // 마지막이 최신
        }
        return (int) latest.values().stream()
                .filter(e -> e.state() == LeaseBindingState.ACTIVE && e.ends() != null && e.ends().isAfter(now))
                .count();
    }
}
