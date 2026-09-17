package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * dnsmasq 임대 파일({@code /var/lib/dnsmasq/dnsmasq.leases}) 파싱(R16 D-9). 한 줄이 한 임대다 —
 * {@code <만료 epoch초> <MAC> <IP> <hostname|*> <client-id|*>}. 만료 {@code 0} 은 무만료(infinite).
 * DHCPv6 줄({@code duid …} · IAID 형식)과 손상 줄은 그 줄만 버린다 — 어떤 입력에도 throw 하지 않는다.
 *
 * <p>dnsmasq 는 만료된 줄을 다음 파일 갱신 때 지우므로 잠시 남아 있을 수 있다. 상태는 모두 ACTIVE 로 두고
 * 활성 판정은 {@link LeaseSnapshot#activeCount}(ends &gt; now) 가 관측 시각으로 한다.</p>
 */
@Component
public class DnsmasqLeaseParser {

    public LeaseSnapshot parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LeaseSnapshot.empty();
        }
        List<LeaseEntry> entries = new ArrayList<>();
        for (String line : raw.split("\n")) {
            try {
                LeaseEntry entry = parseLine(line.strip());
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (RuntimeException e) {
                // 손상 줄(IPv6 · 잘못된 MAC · 숫자 아닌 만료)은 그 줄만 버린다 — 나머지 임대는 계속 파싱.
            }
        }
        return new LeaseSnapshot(entries);
    }

    private LeaseEntry parseLine(String line) {
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("duid ")) {
            return null;
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            return null;
        }
        long expiry = Long.parseLong(tokens[0]);
        IpAddressVO ip = IpAddressVO.of(tokens[2]);          // IPv6 · 잘못된 형식 → throw → 이 줄 skip
        MacAddressVO mac = "*".equals(tokens[1]) ? null : MacAddressVO.of(tokens[1]);
        Instant ends = expiry == 0 ? Instant.MAX : Instant.ofEpochSecond(expiry);
        return new LeaseEntry(ip, mac, ends, LeaseBindingState.ACTIVE);
    }
}
