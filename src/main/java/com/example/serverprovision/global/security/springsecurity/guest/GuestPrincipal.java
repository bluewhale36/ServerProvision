package com.example.serverprovision.global.security.springsecurity.guest;

import java.util.UUID;

/** 게스트 체인이 세운 principal (S19-1 D-3) — 컨트롤러 · 서비스는 토큰 문자열이 아니라 이것을 받는다. */
public record GuestPrincipal(UUID guestServerId, UUID systemUUID) {
}
