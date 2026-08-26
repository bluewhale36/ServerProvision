package com.example.serverprovision.global.redfish;

import com.example.serverprovision.global.bmcweb.AmiWebError;
import com.example.serverprovision.global.bmcweb.AmiWebRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E3-2 D-4 — 폴백 사다리는 프로토콜을 모른다. AMI 웹 API 의 인증 거부도 {@code authFailure()} 하나로 다음 후보로
 * 넘어가고, 인증이 아닌 실패(데이터 거절 · 연결)는 그 자리에서 멈춘다.
 */
class BmcCredentialsFallbackWebTest {

    private static final RedfishTarget TARGET = new RedfishTarget("10.10.0.51", "QG260700082");

    private final BmcCredentialsFallback fallback =
            new BmcCredentialsFallback(new BmcCredentialsResolver("admin", "standard-pw"), new BmcCredentialsMemory());

    @Test
    @DisplayName("웹 로그인이 표준 자격을 cc:7 로 거부하면 공장 기본(시리얼)으로 이어 시도한다")
    void webAuthFailureFallsBackToNextCandidate() {
        List<String> tried = new ArrayList<>();

        String source = fallback.attempt(TARGET, c -> {
            tried.add(c.source());
            if ("standard-pw".equals(c.password())) {
                throw new AmiWebRequestException(AmiWebError.AUTH_FAILED, "POST /api/session — cc:7", 7, null);
            }
            return c.source();
        });

        assertThat(tried).hasSize(2);
        assertThat(source).isEqualTo(tried.get(1));
    }

    @Test
    @DisplayName("인증이 아닌 웹 실패(데이터 거절 · 연결)는 다음 후보로 넘어가지 않고 그 자리에서 올린다")
    void nonAuthWebFailureStops() {
        List<String> tried = new ArrayList<>();

        assertThatThrownBy(() -> fallback.attempt(TARGET, c -> {
            tried.add(c.source());
            throw new AmiWebRequestException(AmiWebError.CONNECT_FAILED, "연결 불가", null, null);
        })).isInstanceOf(AmiWebRequestException.class);

        assertThat(tried).hasSize(1);
    }
}
