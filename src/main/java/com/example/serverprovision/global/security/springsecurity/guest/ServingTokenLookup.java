package com.example.serverprovision.global.security.springsecurity.guest;

import java.util.Optional;
import java.util.UUID;

/**
 * 서빙 토큰 발급소가 게스트 체인에 신원을 알려 주는 SPI(E4-1-b-4-2). 발급소마다 자기 종류를 밝히고, 토큰이 가리키는 게스트를 돌려준다.
 * 게스트 체인은 종류로 발급소를 고를 뿐 발급소 목록을 알지 않는다 — 새 서빙 종류가 생겨도 체인 코드는 그대로다.
 */
public interface ServingTokenLookup {

	GuestCredential.ServingKind kind();

	/** 토큰을 발급받은 게스트 — 없거나 회수됐으면 empty. */
	Optional<UUID> guestOf(UUID token);
}
