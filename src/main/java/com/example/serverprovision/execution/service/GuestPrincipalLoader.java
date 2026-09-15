package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 게스트 체인이 세운 principal({@link GuestPrincipal})을 엔티티로 올린다(S19-1 D-6 · 구 GuestTokenAuthenticator).
 * 토큰 대조는 필터의 provider 로 옮겨졌고, 여기서는 존재 확인과 접촉 관찰({@code lastSeenAt} · DEC-32)만 한다 — 호출자의 트랜잭션에
 * 참여한다. 필터가 세운 principal 이 DB 에 없는 것은 데이터 손상이므로 500 이 정직하다.
 */
@Component
@RequiredArgsConstructor
public class GuestPrincipalLoader {

    private final GuestServerRepository guestServerRepository;

    public GuestServer require(GuestPrincipal guest) {
        GuestServer server = guestServerRepository.findById(guest.guestServerId())
                .orElseThrow(() -> new IllegalStateException("인증된 게스트가 저장소에 없다 : " + guest.guestServerId()));
        server.touchSeen(LocalDateTime.now());
        return server;
    }
}
