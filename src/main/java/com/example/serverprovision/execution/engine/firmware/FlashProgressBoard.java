package com.example.serverprovision.execution.engine.firmware;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 굽기 진행률 게시판(2026-09-17) — 게스트별 <b>마지막 진행률</b> 하나를 인메모리로만 든다({@code WorkerObservations} 와 같은 결).
 * 화면의 축 줄이 "굽는 중 37%" 를 그리는 재료이며, 축이 종결되면 지운다. 저장하지 않는 이유도 같다 — 반복 관측이라 마지막 값만 뜻이 있다.
 */
@Component
public class FlashProgressBoard {

    private static final int MAX_ENTRIES = 512;

    private final Map<UUID, FlashProgress> latest = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, FlashProgress> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    public void note(UUID guestId, FlashProgress progress) {
        latest.put(guestId, progress);
    }

    public Optional<FlashProgress> latestOf(UUID guestId) {
        return Optional.ofNullable(latest.get(guestId));
    }

    public void clear(UUID guestId) {
        latest.remove(guestId);
    }
}
