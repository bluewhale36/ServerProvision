package com.example.serverprovision.execution.engine.firmware;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 집행 시한 정책(E2-2 D-8) — 값과 그 값을 쓰는 계산을 한곳에 둔다. {@link HoldTtlPolicy} 와 동형이며,
 * 다른 것은 <b>축마다 시한이 다르다</b>는 점 하나다.
 *
 * <p>축별로 나눈 이유는 실측 소요가 네 배 가까이 벌어지기 때문이다 — 하나로 덮으면 짧은 쪽이 지나치게
 * 관대해져 벽돌을 늦게 발견한다. 기본값은 {@link FirmwareAxis} 상수가 들고, 여기서는 설정이 있을 때만
 * 덮는다. 설정 키를 축 이름으로 조립하므로 <b>축이 늘어도 이 클래스는 그대로다</b>(D-1).</p>
 *
 * <p>복귀 시한은 축과 무관한 phase 수준 값이다 — 전원을 켠 뒤 게스트가 POST 를 지나 PXE 로 돌아오기를
 * 기다리는 시간이며, 이것을 넘기면 벽돌을 의심한다.</p>
 */
@Component
@RequiredArgsConstructor
public class FlashTimeoutPolicy {

    private static final String FLASH_KEY_PREFIX = "provision.execution.flash-timeout.";
    private static final String RETURN_KEY = "provision.execution.return-timeout";
    private static final Duration DEFAULT_RETURN_LIMIT = Duration.ofMinutes(20);

    private final Environment environment;

    /** 이 축의 굽기 시한 — 설정이 있으면 그 값, 없으면 축 상수의 기본값. */
    public Duration limitFor(FirmwareAxis axis) {
        return durationOf(FLASH_KEY_PREFIX + axis.name().toLowerCase(Locale.ROOT), axis.getDefaultTimeout());
    }

    /** 전원을 켠 뒤 게스트가 돌아오기를 기다리는 시한. */
    public Duration returnLimit() {
        return durationOf(RETURN_KEY, DEFAULT_RETURN_LIMIT);
    }

    /**
     * 설정값을 기간으로 읽는다. {@code Environment} 의 타입 변환은 ISO-8601 만 알아서 {@code 15m}
     * 같은 표기를 받지 못하므로, Boot 가 {@code @Value} 자리에서 쓰는 완화된 파서를 직접 부른다 —
     * 설정 파일의 표기를 다른 시한 설정({@code hold-ttl=48h})과 같은 모양으로 유지하기 위함이다.
     */
    private Duration durationOf(String key, Duration fallback) {
        String raw = environment.getProperty(key);
        return raw == null || raw.isBlank() ? fallback : DurationStyle.detectAndParse(raw);
    }

    public boolean isExpired(LocalDateTime since, Duration limit, LocalDateTime now) {
        return since != null && since.plus(limit).isBefore(now);
    }

    /** 시한까지 남은 분 — 이미 지났으면 0(화면이 음수를 그리지 않게). */
    public long remainingMinutes(LocalDateTime since, Duration limit, LocalDateTime now) {
        if (since == null) {
            return 0L;
        }
        long elapsed = Duration.between(since, now).toMinutes();
        return Math.max(0L, limit.toMinutes() - elapsed);
    }
}
