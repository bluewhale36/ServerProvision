package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1.6 CP4 — 성공 자격 캐시(D-3). 기억 · 재정렬 · 미적용 경계(시리얼 없음)를 검증한다.
 */
class BmcCredentialsMemoryTest {

    private static final BmcCredentials STANDARD = new BmcCredentials("admin", "pw", "표준 계정");
    private static final BmcCredentials FACTORY = new BmcCredentials("admin", "SERIAL123", "공장 기본(보드 시리얼)");

    private final BmcCredentialsMemory memory = new BmcCredentialsMemory();

    @Test
    @DisplayName("기억이 없으면 원본 순서 그대로")
    void noMemory_keepsOrder() {
        assertThat(memory.preferredOrder("SERIAL123", List.of(STANDARD, FACTORY)))
                .containsExactly(STANDARD, FACTORY);
    }

    @Test
    @DisplayName("성공을 기억하면 그 후보가 앞으로 온다 — 신품의 매 폴링 401 노이즈가 사라지는 원리")
    void remembered_comesFirst() {
        memory.remember("SERIAL123", "공장 기본(보드 시리얼)");

        assertThat(memory.preferredOrder("SERIAL123", List.of(STANDARD, FACTORY)))
                .containsExactly(FACTORY, STANDARD);
    }

    @Test
    @DisplayName("다른 시리얼의 기억은 영향을 주지 않는다 — 게스트 단위 캐시")
    void otherSerial_notAffected() {
        memory.remember("OTHER", "공장 기본(보드 시리얼)");

        assertThat(memory.preferredOrder("SERIAL123", List.of(STANDARD, FACTORY)))
                .containsExactly(STANDARD, FACTORY);
    }

    @Test
    @DisplayName("시리얼이 없으면 캐시 미적용(원본 그대로) · 기억도 남기지 않는다")
    void nullSerial_bypassed() {
        memory.remember(null, "표준 계정");
        memory.remember(" ", "표준 계정");

        assertThat(memory.preferredOrder(null, List.of(STANDARD, FACTORY)))
                .containsExactly(STANDARD, FACTORY);
    }

    @Test
    @DisplayName("표준화 성공 뒤 캐시가 스스로 갱신된다 — 마지막 성공이 이긴다")
    void lastSuccess_wins() {
        memory.remember("SERIAL123", "공장 기본(보드 시리얼)");
        memory.remember("SERIAL123", "표준 계정");

        assertThat(memory.preferredOrder("SERIAL123", List.of(STANDARD, FACTORY)))
                .containsExactly(STANDARD, FACTORY);
    }
}
