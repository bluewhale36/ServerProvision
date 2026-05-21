package com.example.serverprovision.global.lifecycle;

import java.util.UUID;

/**
 * MK3-2 (DCM3-2.6) — DeleteIntentRegistry 의 1회용 token.
 * <p>NudgeRegistry 의 UUID 사용 패턴과 동형. 직렬화 시 {@code "del-<uuid>"} prefix 로 시각적 구분.</p>
 */
public record DeleteIntentToken(UUID value) {

	public static DeleteIntentToken issue() {
		return new DeleteIntentToken(UUID.randomUUID());
	}

	public static DeleteIntentToken parse(String prefixed) {
		if (prefixed == null || !prefixed.startsWith("del-")) {
			throw new IllegalArgumentException("올바른 DeleteIntentToken 형식이 아닙니다 : " + prefixed);
		}
		return new DeleteIntentToken(UUID.fromString(prefixed.substring(4)));
	}

	/**
	 * 응답 / Path variable 등 외부 노출용 직렬화. {@code "del-<uuid>"}.
	 */
	public String asString() {
		return "del-" + value;
	}
}
