package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ISC dhcpd.leases 파싱. {@code lease <ip> { ... }} 블록 단위 try/catch 로 손상 블록·미지 상태·VO throw·빈 파일을
 * 전부 흡수 — 절대 throw 하지 않는다. 파일 부재는 호출자가 흡수(여기 도달 안 함). 타임스탬프는 UTC.
 *
 * <p>무상태 순수 클래스라 스프링 빈으로 두되(공유 재사용) 필드가 없다. 정규식은 컴파일 상수로 재사용한다.</p>
 */
@Component
public class DhcpLeaseParser {

    // lease 블록: `lease <ip> { ... }`. body 에서 중괄호 자체를 배제(`[^{}]*`)해 단일 레벨 블록만 매칭한다 —
    // dhcpd.leases 는 중괄호를 중첩하지 않으므로 이 제약이 정상 블록을 놓치지 않는다. 미종료 블록(닫는 `}` 없음)은
    // 다음 블록의 여는 `{` 에서 매칭이 끊겨 스스로 제외되고, 뒤따르는 정상 블록은 독립적으로 매칭된다(그 블록만 skip).
    private static final Pattern LEASE_BLOCK = Pattern.compile(
            "lease\\s+(\\S+)\\s*\\{([^{}]*)\\}");

    // starts/ends 의 타임스탬프 = `<weekday> YYYY/MM/dd HH:mm:ss`. weekday(요일 숫자)는 버리고 시각만 취한다.
    private static final Pattern STARTS = Pattern.compile(
            "\\bstarts\\s+\\d+\\s+(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s*;");
    // ends 는 `never` 이거나 타임스탬프. never = 만료 없음.
    private static final Pattern ENDS = Pattern.compile(
            "\\bends\\s+(never|\\d+\\s+\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s*;");
    // binding state — `next binding state` 와 구분하기 위해 "next " 선행을 배제한다.
    private static final Pattern BINDING_STATE = Pattern.compile(
            "(?<!next )\\bbinding state\\s+(\\S+?)\\s*;");
    private static final Pattern HARDWARE = Pattern.compile(
            "\\bhardware ethernet\\s+(\\S+?)\\s*;");

    private static final DateTimeFormatter LEASE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    /** ISC dhcpd.leases 파싱. 빈 문자열·null → 빈 스냅샷. 손상 블록은 그 블록만 skip. */
    public LeaseSnapshot parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LeaseSnapshot.empty();
        }
        List<LeaseEntry> entries = new ArrayList<>();
        Matcher blocks = LEASE_BLOCK.matcher(raw);
        while (blocks.find()) {
            try {
                entries.add(parseBlock(blocks.group(1), blocks.group(2)));
            } catch (RuntimeException e) {
                // 손상 블록(잘못된 IP·시각 형식 등)은 그 블록만 버린다 — 나머지 임대는 계속 파싱.
            }
        }
        return new LeaseSnapshot(entries);
    }

    private LeaseEntry parseBlock(String rawIp, String body) {
        IpAddressVO ip = IpAddressVO.of(rawIp);   // IPv6·잘못된 형식 → throw → 이 블록 skip
        Instant starts = matchTime(STARTS, body);
        Instant ends = matchEnds(body);
        MacAddressVO mac = matchMac(body);
        LeaseBindingState state = matchState(body);
        return new LeaseEntry(ip, mac, starts, ends, state);
    }

    private Instant matchTime(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        if (!m.find()) {
            return null;
        }
        return toInstant(m.group(1));
    }

    private Instant matchEnds(String body) {
        Matcher m = ENDS.matcher(body);
        if (!m.find()) {
            return null;
        }
        String value = m.group(1).strip();
        if ("never".equals(value)) {
            // 잠정: never → Instant.MAX 로 취급해 activeCount 에서 active 로 셈. CP4 서명 전 실측으로 never 빈도 확인.
            return Instant.MAX;
        }
        // value = `<weekday> YYYY/MM/dd HH:mm:ss` — 요일 숫자를 떼고 시각만 취한다.
        String timePart = value.substring(value.indexOf(' ') + 1);
        return toInstant(timePart);
    }

    private MacAddressVO matchMac(String body) {
        Matcher m = HARDWARE.matcher(body);
        if (!m.find()) {
            return null;   // 하드웨어 미기재 블록 — mac 은 nullable
        }
        return MacAddressVO.of(m.group(1));
    }

    private LeaseBindingState matchState(String body) {
        Matcher m = BINDING_STATE.matcher(body);
        return m.find() ? LeaseBindingState.from(m.group(1)) : LeaseBindingState.UNKNOWN;
    }

    private Instant toInstant(String timestamp) {
        return LocalDateTime.parse(timestamp.strip(), LEASE_TIME).toInstant(ZoneOffset.UTC);
    }
}
