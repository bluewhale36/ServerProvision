package com.example.serverprovision.execution.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** E2-4 Q2 · O-3 — 마지막 관측 하나 · FIFO 상한 · 회수 직전 파괴. */
class WorkerObservationsTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 31, 12, 0);

    @Test
    @DisplayName("마지막 관측 하나만 남는다 — 같은 게스트의 새 관측이 덮어쓴다")
    void latestWins() {
        WorkerObservations observations = new WorkerObservations();
        UUID guest = UUID.randomUUID();

        observations.note(guest, "집행 상태 점검", T);
        observations.note(guest, "BMC Task 확인 — 굽는 중", T.plusSeconds(30));

        assertThat(observations.latestOf(guest)).hasValueSatisfying(o -> {
            assertThat(o.note()).isEqualTo("BMC Task 확인 — 굽는 중");
            assertThat(o.at()).isEqualTo(T.plusSeconds(30));
        });
    }

    @Test
    @DisplayName("FIFO 축출(O-3) — 상한을 넘으면 가장 오래된 관측부터 사라진다")
    void fifoEviction() {
        WorkerObservations observations = new WorkerObservations();
        UUID first = UUID.randomUUID();
        observations.note(first, "첫 관측", T);
        for (int i = 0; i < 512; i++) {
            observations.note(UUID.randomUUID(), "관측 " + i, T.plusSeconds(i));
        }

        assertThat(observations.latestOf(first)).isEmpty();
    }

    @Test
    @DisplayName("회수 직전 파괴(O-3) — clear 뒤에는 지난 집행의 관측이 남지 않는다")
    void clearRemoves() {
        WorkerObservations observations = new WorkerObservations();
        UUID guest = UUID.randomUUID();
        observations.note(guest, "집행 상태 점검", T);

        observations.clear(guest);

        assertThat(observations.latestOf(guest)).isEmpty();
    }
}
