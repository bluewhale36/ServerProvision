package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R16 — dnsmasq 임대 파일 파서. 한 줄 = {@code <만료 epoch> <MAC> <IP> <hostname|*> <client-id|*>}. 어떤 입력에도 throw 하지
 * 않고 손상 줄 · IPv6(duid · IAID) 줄 · 빈 파일을 흡수하며, 활성 집계는 관측 시각 기준이다.
 */
class DnsmasqLeaseParserTest {

    private final DnsmasqLeaseParser parser = new DnsmasqLeaseParser();

    /** 관측 기준 시각 — 2026-09-16 07:00:00 UTC = epoch 1789542000 부근을 기준으로 만료 앞뒤를 둔다. */
    private static final Instant NOW = Instant.ofEpochSecond(1_789_542_000L);

    private static final String LEASES = """
            1789545600 52:54:00:12:34:56 192.168.1.101 spv-guest-1 01:52:54:00:12:34:56
            1789500000 52:54:00:12:34:57 192.168.1.102 * *
            0 52:54:00:12:34:58 192.168.1.103 forever *
            duid 00:01:00:01:2c:3d:4e:5f:52:54:00:12:34:56
            1789545600 1234567 fd00::1 v6host 00:01:00:01
            garbage line without enough tokens
            notanumber 52:54:00:12:34:59 192.168.1.104 * *
            """;

    @Test
    @DisplayName("정상 3줄만 파싱 — duid · IPv6 · 손상 줄은 그 줄만 버린다")
    void parsesValidLinesOnly() {
        LeaseSnapshot snapshot = parser.parse(LEASES);

        assertThat(snapshot.entries()).hasSize(3);
        assertThat(snapshot.entries()).extracting(e -> e.ip().value())
                .containsExactly("192.168.1.101", "192.168.1.102", "192.168.1.103");
        assertThat(snapshot.entries()).allMatch(e -> e.state() == LeaseBindingState.ACTIVE);
    }

    @Test
    @DisplayName("만료 epoch → Instant · 0 은 무만료(Instant.MAX) · MAC 은 VO")
    void mapsFields() {
        LeaseSnapshot snapshot = parser.parse(LEASES);

        LeaseEntry first = snapshot.entries().get(0);
        assertThat(first.ends()).isEqualTo(Instant.ofEpochSecond(1_789_545_600L));
        assertThat(first.mac().value()).isEqualToIgnoringCase("52:54:00:12:34:56");
        assertThat(snapshot.entries().get(2).ends()).isEqualTo(Instant.MAX);
    }

    @Test
    @DisplayName("활성 집계 — 만료가 관측 시각 뒤인 줄만(무만료 포함) · 지난 줄은 남아 있어도 세지 않는다")
    void activeCountByNow() {
        assertThat(parser.parse(LEASES).activeCount(NOW)).isEqualTo(2);   // .101(미래) + .103(무만료) · .102 는 지남
    }

    @Test
    @DisplayName("빈 입력 · null → 빈 스냅샷(throw 없음)")
    void emptyInputs() {
        assertThat(parser.parse("").entries()).isEmpty();
        assertThat(parser.parse(null).entries()).isEmpty();
        assertThat(parser.parse("   \n\n").entries()).isEmpty();
    }
}
