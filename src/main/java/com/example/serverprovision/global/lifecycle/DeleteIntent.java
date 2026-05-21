package com.example.serverprovision.global.lifecycle;

import com.example.serverprovision.global.marker.ResourceType;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * MK3-2 (DCM3-2.6) — softDelete reject 시 발급된 intent 의 메타데이터.
 *
 * <p>Registry 가 token 키로 본 record 를 보관. modal 의 두 번째 호출 시 token + (resourceType, resourceId)
 * 일치 검증.</p>
 *
 * @param token          1회용 token (UUID 기반)
 * @param resourceType   자원 종류 (도메인 식별)
 * @param resourceId     자원 PK
 * @param missingPath    사전조건 검사 시점의 부재 경로 (사용자 modal 안내용)
 * @param ghostCandidate 이미 ghost 상태인지 (is_deleted=true + trashed_path=null + Files.notExists). 도입 시점의 잔존 ghost 식별
 * @param issuedAt       token 발급 시각
 * @param expiresAt      만료 시각 (issuedAt + 5분)
 */
public record DeleteIntent(
		DeleteIntentToken token,
		ResourceType resourceType,
		Long resourceId,
		Path missingPath,
		boolean ghostCandidate,
		Instant issuedAt,
		Instant expiresAt
) {

	public static final Duration TTL = Duration.ofMinutes(5);

	public static DeleteIntent issue(ResourceType resourceType, Long resourceId, Path missingPath, boolean ghostCandidate) {
		Instant now = Instant.now();
		return new DeleteIntent(
				DeleteIntentToken.issue(),
				resourceType,
				resourceId,
				missingPath,
				ghostCandidate,
				now,
				now.plus(TTL)
		);
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	/**
	 * modal 두 번째 호출의 token + 자원 매칭 검증.
	 */
	public boolean matches(ResourceType type, Long id) {
		return resourceType == type && resourceId.equals(id);
	}
}
