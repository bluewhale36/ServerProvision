package com.example.serverprovision.execution.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 워커 하트비트(E2-4 Q2) — 게스트별 <b>마지막 관측</b> 하나를 인메모리로만 든다. 저장하지 않는 이유는
 * 반복 관측이라 마지막 값만 의미가 있기 때문이고(토론 Q2), 재기동 소실은 다음 워커 주기가 메운다.
 * 보관은 FIFO — 상한을 넘으면 가장 오래된 관측부터 버린다(O-3 확정).
 */
@Component
public class WorkerObservations {

    private static final int MAX_ENTRIES = 512;

    private final Map<UUID, Observation> latest = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Observation> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    /** 관측 한 건 — 문구와 시각뿐이다. */
    public record Observation(String note, LocalDateTime at) {
    }

    public void note(UUID guestId, String note, LocalDateTime at) {
        latest.put(guestId, new Observation(note, at));
    }

    public Optional<Observation> latestOf(UUID guestId) {
        return Optional.ofNullable(latest.get(guestId));
    }

    /** 회수 처리 직전 파괴(O-3 확정) — 재투입된 서버의 화면이 지난 집행의 관측으로 거짓을 말하지 않게. */
    public void clear(UUID guestId) {
        latest.remove(guestId);
    }
}
