package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * E4-1-a-4 CP4 — 게스트 토큰 인증(D-5, AgentReportService 에서 추출). 일치하면 게스트 + lastSeenAt 갱신, 불일치 · 공백은 404(존재 은닉).
 */
@ExtendWith(MockitoExtension.class)
class GuestTokenAuthenticatorTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";

    @Mock GuestServerRepository guestServerRepository;
    @InjectMocks GuestTokenAuthenticator authenticator;

    @Test
    @DisplayName("일치 → 게스트 반환 + lastSeenAt 갱신(DEC-32)")
    void match_returnsAndTouches() {
        GuestServer g = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
        given(guestServerRepository.findByGuestToken(new GuestToken(TOKEN))).willReturn(Optional.of(g));

        assertThat(authenticator.requireByToken(TOKEN)).isSameAs(g);
        assertThat(g.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("불일치 · 공백 · null → GuestServerNotFound(404) — 공백은 저장소를 묻지도 않는다")
    void mismatchOrBlank_404() {
        given(guestServerRepository.findByGuestToken(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.requireByToken("deadbeef")).isInstanceOf(GuestServerNotFoundException.class);
        assertThatThrownBy(() -> authenticator.requireByToken(" ")).isInstanceOf(GuestServerNotFoundException.class);
        assertThatThrownBy(() -> authenticator.requireByToken(null)).isInstanceOf(GuestServerNotFoundException.class);
        verify(guestServerRepository, org.mockito.Mockito.times(1)).findByGuestToken(any());   // deadbeef 한 번뿐 — 공백 · null 은 저장소 전에 거절
    }
}
