package com.example.serverprovision.global.marker;

import java.time.Instant;
import java.util.Map;

/**
 * `.provision.json` 마커 파일의 직렬화 형식 (record).
 * <p>BIOS 특화였던 기존 v3 MarkerContent 를 일반화. 도메인별 부속 정보는 {@link #attributes}
 * 맵에 흡수하고, 인프라는 도메인 모르고 검증만 수행한다.</p>
 *
 * <p>HMAC 서명 계산 시 {@link #signature} 필드를 null 로 두고 나머지 필드를 canonical JSON 직렬화 후
 * 그 결과 + 시크릿으로 HmacSHA256 을 계산한다.</p>
 *
 * @param resourceType {@code "BIOS_BUNDLE"} 등 {@link ResourceType} 의 name. 문자열로 보존.
 * @param resourceId   엔티티 PK
 * @param attributes   도메인별 부속 (예: BIOS — {@code boardId}, {@code version}, {@code entrypointRelativePath};
 *                     ISO — {@code osImageId}, {@code originalFilename})
 * @param createdAt    마커 발급 시각
 * @param manifestHash 자원 무결성 해시. 디렉토리는 canonicalized 트리, 단일 파일은 SHA-256(bytes)
 * @param signature    HMAC-SHA256 (signature 제외 직렬화 + secret). 서명 검증 시점엔 본인 비교용
 */
public record MarkerContent(
		String resourceType,
		Long resourceId,
		Map<String, String> attributes,
		Instant createdAt,
		String manifestHash,
		String signature
) {

	/**
	 * 서명 계산용 사본 — signature 만 null 로 비운 동일 record 반환.
	 */
	public MarkerContent withoutSignature() {
		return new MarkerContent(resourceType, resourceId, attributes, createdAt, manifestHash, null);
	}

	/**
	 * 서명을 채운 사본 반환. {@link ProvisionMarkerService} 가 signature 계산 후 호출.
	 */
	public MarkerContent withSignature(String signature) {
		return new MarkerContent(resourceType, resourceId, attributes, createdAt, manifestHash, signature);
	}
}
