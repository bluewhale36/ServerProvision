package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1.6 CP4 — 폴백 순회의 캐시 배선(D-3). 성공을 기억하고 다음 순회가 그 후보부터 시도하는지,
 * 자격 무관 실패(연결 등)에서는 기억이 남지 않는지를 검증한다.
 */
class BmcCredentialsFallbackTest {

    private static final RedfishTarget TARGET = new RedfishTarget("10.0.0.9", "SERIAL123");

    private final BmcCredentialsResolver resolver = new BmcCredentialsResolver("admin", "standard-pw");
    private final BmcCredentialsMemory memory = new BmcCredentialsMemory();
    private final BmcCredentialsFallback fallback = new BmcCredentialsFallback(resolver, memory);

    private static RedfishRequestException unauthorized() {
        return new RedfishRequestException(RedfishError.AUTH_FAILED, "GET /x — 자격증명 거부(401)", null);
    }

    @Test
    @DisplayName("신품 재연 — 첫 순회는 표준 401 → 공장 기본 성공, 다음 순회는 공장 기본부터 시도한다")
    void success_remembered_reordersNextAttempt() {
        List<String> attempted = new ArrayList<>();

        String first = fallback.attempt(TARGET, c -> {
            attempted.add(c.source());
            if ("표준 계정".equals(c.source())) {
                throw unauthorized();
            }
            return "ok";
        });
        String second = fallback.attempt(TARGET, c -> {
            attempted.add(c.source());
            if ("표준 계정".equals(c.source())) {
                throw unauthorized();
            }
            return "ok";
        });

        assertThat(first).isEqualTo("ok");
        assertThat(second).isEqualTo("ok");
        // 첫 순회: 표준 → 공장 기본(2회 시도). 둘째 순회: 캐시로 공장 기본이 앞 — 한 번에 끝난다.
        assertThat(attempted).containsExactly("표준 계정", "공장 기본(보드 시리얼)", "공장 기본(보드 시리얼)");
    }

    @Test
    @DisplayName("자격 무관 실패(연결 불가)는 기억을 남기지 않는다")
    void connectFailure_notRemembered() {
        assertThatThrownBy(() -> fallback.attempt(TARGET, c -> {
            throw new RedfishRequestException(RedfishError.CONNECT_FAILED, "GET /x — 연결 실패", null);
        })).isInstanceOf(RedfishRequestException.class);

        List<String> attempted = new ArrayList<>();
        fallback.attempt(TARGET, c -> {
            attempted.add(c.source());
            return "ok";
        });
        assertThat(attempted).containsExactly("표준 계정");   // 재정렬 없음 — 원 순서 유지
    }
}
