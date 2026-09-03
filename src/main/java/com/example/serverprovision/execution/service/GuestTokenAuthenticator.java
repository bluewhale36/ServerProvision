package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.vo.GuestToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 게스트 토큰 인증(E4-1-a-4 D-5) — 진단 에이전트 창구({@code AgentReportService})에 있던 {@code requireByToken} 을
 * Windows 완료 보고 창구와 공유하려고 추출했다(동작 무변경). 불일치 · 공백은 404 로 존재를 숨기고(plan Q2), 인증된
 * 접촉은 {@code lastSeenAt} 관찰 로그를 갱신한다(DEC-32) — 호출자의 트랜잭션에 참여한다.
 */
@Component
@RequiredArgsConstructor
public class GuestTokenAuthenticator {

    private final GuestServerRepository guestServerRepository;

    public GuestServer requireByToken(String presented) {
        if (presented == null || presented.isBlank()) {
            throw GuestServerNotFoundException.byToken();
        }
        GuestServer server = guestServerRepository.findByGuestToken(new GuestToken(presented))
                .orElseThrow(GuestServerNotFoundException::byToken);
        // 게이트 거절(409) 시엔 롤백으로 함께 사라지지만, 그런 게스트도 /boot 폴링은 계속 하므로 관찰 공백은 없다.
        server.touchSeen(LocalDateTime.now());
        return server;
    }
}
