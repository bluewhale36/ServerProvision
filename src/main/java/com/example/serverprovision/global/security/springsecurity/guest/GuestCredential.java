package com.example.serverprovision.global.security.springsecurity.guest;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * 게스트가 제시하는 credential (S19-1 D-2). 헤더 토큰은 에이전트 채널({@code X-Guest-Token}), 서빙 토큰은 Windows 번들 · 펌웨어 파일 ·
 * RHEL 설치 원본의 경로 세그먼트다. 서빙 경로에서는 경로 토큰이 곧 credential 이라 헤더를 읽지 않는다.
 */
public sealed interface GuestCredential permits GuestCredential.HeaderToken, GuestCredential.ServingToken {

	record HeaderToken(String value) implements GuestCredential {
	}

	record ServingToken(UUID value, ServingKind kind) implements GuestCredential {
	}

	/** 서빙 토큰의 종류. URL 의 {@code /api/pxe/v1/<segment>/<token>/…} 세그먼트가 종류를 정한다 — 발급소의 URL 조립과 인증 판독이 같은 값을 쓴다. */
	enum ServingKind {
		WINDOWS("windows"), FIRMWARE("firmware"), RHEL("rhel");

		private final String segment;

		ServingKind(String segment) {
			this.segment = segment;
		}

		public String segment() {
			return segment;
		}

		public static Optional<ServingKind> ofSegment(String segment) {
			return Arrays.stream(values()).filter(kind -> kind.segment.equals(segment)).findFirst();
		}
	}
}
