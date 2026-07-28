package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ISC dhcpd.leases 파서 검증 — 파서는 어떤 입력에도 절대 throw 하지 않고, 손상 블록·미지 상태·하드웨어 미기재·
 * 빈 파일을 전부 상태로 흡수한다. binding state 4종 이상·손상 블록·필드 결손·중괄호 미종료·{@code ends never}·
 * 활성 임대 집계(IP dedup 포함)를 확인한다.
 *
 * <p><b>픽스처는 잠정</b>(Rocky 9 실측 전, D7·Q2)이다 — 실제 ISC dhcpd.leases 문법으로 손수 작성했으나 CP4 서명
 * 전 실 서버 캡처로 교체 예정이다. 형식(요일 숫자 + {@code YYYY/MM/dd HH:mm:ss}, {@code next binding state} 라인,
 * {@code hardware ethernet}, {@code client-hostname})은 ISC dhcpd 관례를 따랐다.</p>
 */
class DhcpLeaseParserTest {

    private final DhcpLeaseParser parser = new DhcpLeaseParser();

    /** 관측 기준 시각 — 활성 판정(ends > now)의 경계. UTC. */
    private static final Instant NOW = Instant.parse("2026-07-28T06:00:00Z");

    // binding state 4종 이상 + mac 유무 + starts/ends 유무 + 미지 상태를 한 파일에 담은 잠정 픽스처.
    private static final String LEASES = """
            # 잠정 픽스처 (Rocky 9 실측 전, D7·Q2) — CP4 서명 전 실측 캡처로 교체 예정.
            lease 10.0.2.50 {
              starts 2 2026/07/28 01:00:00;
              ends 2 2026/07/28 13:00:00;
              cltt 2 2026/07/28 01:00:00;
              binding state active;
              next binding state free;
              hardware ethernet 52:54:00:12:34:56;
              client-hostname "node-alpha";
            }
            lease 10.0.2.51 {
              starts 2 2026/07/27 20:00:00;
              ends 2 2026/07/28 02:00:00;
              binding state active;
              next binding state free;
              hardware ethernet 52:54:00:aa:bb:cc;
            }
            lease 10.0.2.52 {
              starts 2 2026/07/27 10:00:00;
              ends 2 2026/07/27 22:00:00;
              binding state expired;
              hardware ethernet 52:54:00:de:ad:be;
            }
            lease 10.0.2.53 {
              starts 2 2026/07/27 09:00:00;
              ends 2 2026/07/27 21:00:00;
              binding state free;
            }
            lease 10.0.2.54 {
              binding state backup;
              hardware ethernet 52:54:00:ff:ee:dd;
            }
            lease 10.0.2.55 {
              binding state abandoned;
            }
            lease 10.0.2.56 {
              binding state weirdstate;
              hardware ethernet 52:54:00:01:02:03;
            }
            """;

    @Test
    @DisplayName("빈 입력 — null·빈 문자열·공백은 모두 빈 스냅샷(throw 없음)")
    void parse_nullOrBlank_returnsEmpty() {
        assertThat(parser.parse(null).entries()).isEmpty();
        assertThat(parser.parse("").entries()).isEmpty();
        assertThat(parser.parse("   \n  ").entries()).isEmpty();
    }

    @Test
    @DisplayName("binding state 다종 — active/expired/free/backup/abandoned + 미지 상태(UNKNOWN)로 흡수")
    void parse_variousBindingStates_mapped() {
        Map<String, LeaseEntry> byIp = index(LEASES);

        assertThat(byIp).hasSize(7);
        assertThat(byIp.get("10.0.2.50").state()).isEqualTo(LeaseBindingState.ACTIVE);
        assertThat(byIp.get("10.0.2.52").state()).isEqualTo(LeaseBindingState.EXPIRED);
        assertThat(byIp.get("10.0.2.53").state()).isEqualTo(LeaseBindingState.FREE);
        assertThat(byIp.get("10.0.2.54").state()).isEqualTo(LeaseBindingState.BACKUP);
        assertThat(byIp.get("10.0.2.55").state()).isEqualTo(LeaseBindingState.ABANDONED);
        // 미지 상태 문자열은 예외가 아니라 UNKNOWN 으로 흡수한다.
        assertThat(byIp.get("10.0.2.56").state()).isEqualTo(LeaseBindingState.UNKNOWN);
    }

    @Test
    @DisplayName("필드 결손 — 하드웨어 미기재는 mac null, starts/ends 미기재는 null(블록 유지)")
    void parse_missingFields_absorbedAsNull() {
        Map<String, LeaseEntry> byIp = index(LEASES);

        // 53 은 hardware 라인이 없다 → mac null.
        assertThat(byIp.get("10.0.2.53").mac()).isNull();
        // 54 는 starts/ends 라인이 없다 → 시각 null, 그래도 블록은 살아있다.
        LeaseEntry backup = byIp.get("10.0.2.54");
        assertThat(backup.starts()).isNull();
        assertThat(backup.ends()).isNull();
        // 50 은 mac·시각 모두 정상 파싱.
        assertThat(byIp.get("10.0.2.50").mac().value()).isEqualTo("52:54:00:12:34:56");
        assertThat(byIp.get("10.0.2.50").ends()).isEqualTo(Instant.parse("2026-07-28T13:00:00Z"));
    }

    @Test
    @DisplayName("손상 블록 — 잘못된 IP 블록은 그 블록만 skip, 정상 블록은 유지")
    void parse_corruptIpBlocks_skippedIndividually() {
        String raw = """
                lease 10.0.2.80 {
                  binding state active;
                  ends never;
                }
                lease 10.0.2.999 {
                  binding state active;
                  ends never;
                }
                lease 300.1.1.1 {
                  binding state active;
                  ends never;
                }
                """;

        Map<String, LeaseEntry> byIp = index(raw);

        // 잘못된 옥텟(999·300)은 IpAddressVO 가 거절 → 그 블록만 버리고 유효 블록만 남는다.
        assertThat(byIp).hasSize(1);
        assertThat(byIp).containsKey("10.0.2.80");
    }

    @Test
    @DisplayName("중괄호 미종료 블록 — 닫히지 않은 마지막 블록은 매칭되지 않아 조용히 제외")
    void parse_unclosedBlock_ignored() {
        String raw = """
                lease 10.0.2.90 {
                  binding state active;
                  ends never;
                }
                lease 10.0.2.91 {
                  binding state active;
                """;

        Map<String, LeaseEntry> byIp = index(raw);

        assertThat(byIp).hasSize(1);
        assertThat(byIp).containsKey("10.0.2.90");
    }

    @Test
    @DisplayName("중간 미종료 블록 — 닫히지 않은 블록이 뒤 정상 블록을 삼키지 않고 그 블록만 제외")
    void parse_midFileUnclosedBlock_doesNotSwallowNext() {
        String raw = """
                lease 10.0.2.1 {
                  binding state active;
                lease 10.0.2.2 {
                  binding state active;
                  ends never;
                }
                """;

        Map<String, LeaseEntry> byIp = index(raw);

        // 10.0.2.1 은 닫는 `}` 없이 다음 블록으로 이어져 매칭이 끊긴다 → 그 블록만 제외.
        // 뒤따르는 정상 블록 10.0.2.2 는 독립적으로 매칭되어 살아남는다(손상이 정상 데이터를 삼키지 않음).
        assertThat(byIp).hasSize(1);
        assertThat(byIp).containsKey("10.0.2.2");
    }

    @Test
    @DisplayName("ends never — 무만료는 Instant.MAX 로 담아 activeCount 에서 활성으로 센다")
    void parse_endsNever_treatedAsNonExpiring() {
        String raw = """
                lease 10.0.2.70 {
                  starts 2 2026/07/28 01:00:00;
                  ends never;
                  binding state active;
                  hardware ethernet 52:54:00:11:22:33;
                }
                """;

        LeaseSnapshot snapshot = parser.parse(raw);

        assertThat(snapshot.entries()).hasSize(1);
        assertThat(snapshot.entries().get(0).ends()).isEqualTo(Instant.MAX);
        assertThat(snapshot.activeCount(NOW)).isEqualTo(1);
    }

    @Test
    @DisplayName("activeCount — state==ACTIVE 이고 ends>now 인 임대만 센다(만료·비활성 제외)")
    void activeCount_countsOnlyActiveUnexpired() {
        LeaseSnapshot snapshot = parser.parse(LEASES);

        // 50: active·ends 13:00(>06:00) → 카운트. 51: active 이나 ends 02:00(<06:00) → 만료로 제외.
        // 나머지(expired/free/backup/abandoned/unknown)는 상태로 제외 → 활성 1건.
        assertThat(snapshot.activeCount(NOW)).isEqualTo(1);
    }

    @Test
    @DisplayName("activeCount dedup — 같은 IP 는 파일상 마지막 블록이 최신(ISC append). 최신이 free 면 활성 아님")
    void activeCount_dedupByIp_latestBlockWins() {
        String raw = """
                lease 10.0.2.60 {
                  ends 2 2026/07/28 20:00:00;
                  binding state active;
                }
                lease 10.0.2.60 {
                  ends 2 2026/07/28 22:00:00;
                  binding state free;
                }
                """;

        LeaseSnapshot snapshot = parser.parse(raw);

        // 블록은 2개지만 같은 IP → 최신(뒤) 블록만 유효. 최신이 free 라 활성 0.
        assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.activeCount(NOW)).isZero();
    }

    /** IP → LeaseEntry 로 색인(같은 IP 는 마지막 블록이 덮어씀 — 스냅샷 순서 보존). */
    private Map<String, LeaseEntry> index(String raw) {
        Map<String, LeaseEntry> byIp = new LinkedHashMap<>();
        for (LeaseEntry e : parser.parse(raw).entries()) {
            byIp.put(e.ip().value(), e);
        }
        return byIp;
    }
}
