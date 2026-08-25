package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E1.5 CP4 — 자격증명 후보 순서(표준 계정 → 공장 기본 = 보드 시리얼 11자, P1 · OQ2). */
class BmcCredentialsResolverTest {

    @Test
    @DisplayName("표준 비밀번호 + 시리얼 → 후보 2(표준 먼저), username 은 둘 다 admin")
    void standardThenFactory() {
        BmcCredentialsResolver resolver = new BmcCredentialsResolver("admin", "standard-pw");
        List<BmcCredentials> candidates = resolver.candidates("QG260700082");
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).password()).isEqualTo("standard-pw");
        assertThat(candidates.get(1).password()).isEqualTo("QG260700082");
        assertThat(candidates).allMatch(c -> c.username().equals("admin"));
    }

    @Test
    @DisplayName("표준 비밀번호 미설정 → 시리얼 폴백만 · 시리얼도 없으면 빈 목록(부팅은 막지 않는다)")
    void fallbackOnlyOrEmpty() {
        BmcCredentialsResolver resolver = new BmcCredentialsResolver("admin", "");
        assertThat(resolver.candidates("QG260700082")).hasSize(1)
                .first().satisfies(c -> assertThat(c.source()).contains("공장 기본"));
        assertThat(resolver.candidates(null)).isEmpty();
        assertThat(resolver.candidates("  ")).isEmpty();
    }

    @Test
    @DisplayName("종류별 접근자 — 사다리(E1.6 D-2)가 순서 아닌 정체로 판정하는 재료")
    void typedCandidates() {
        BmcCredentialsResolver resolver = new BmcCredentialsResolver("admin", "standard-pw");

        org.assertj.core.api.Assertions.assertThat(resolver.standardCandidate())
                .hasValueSatisfying(c -> org.assertj.core.api.Assertions.assertThat(c.source()).isEqualTo("표준 계정"));
        org.assertj.core.api.Assertions.assertThat(resolver.factoryCandidate("SERIAL123"))
                .hasValueSatisfying(c -> org.assertj.core.api.Assertions.assertThat(c.password()).isEqualTo("SERIAL123"));
        org.assertj.core.api.Assertions.assertThat(resolver.factoryCandidate(null)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(new BmcCredentialsResolver("admin", "").standardCandidate()).isEmpty();
    }

    @Test
    @DisplayName("toString — 비밀번호는 마스킹된다 (로그 · 디버그 어디에도 평문 0)")
    void toStringMasksPassword() {
        org.assertj.core.api.Assertions.assertThat(new BmcCredentials("admin", "secret-pw", "표준 계정").toString())
                .doesNotContain("secret-pw").contains("password=*");
    }
}
