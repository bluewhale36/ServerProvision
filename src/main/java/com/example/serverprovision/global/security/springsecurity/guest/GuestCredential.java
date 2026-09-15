package com.example.serverprovision.global.security.springsecurity.guest;

import java.util.UUID;

/**
 * 게스트가 제시하는 credential (S19-1 D-2). 헤더 토큰은 에이전트 채널({@code X-Guest-Token}), 서빙 토큰은 Windows 번들 · 펌웨어 파일의
 * 경로 세그먼트다. 서빙 경로에서는 경로 토큰이 곧 credential 이라 헤더를 읽지 않는다.
 */
public sealed interface GuestCredential permits GuestCredential.HeaderToken, GuestCredential.ServingToken {

	record HeaderToken(String value) implements GuestCredential {
	}

	record ServingToken(UUID value, ServingKind kind) implements GuestCredential {
	}

	enum ServingKind {
		WINDOWS, FIRMWARE
	}
}
