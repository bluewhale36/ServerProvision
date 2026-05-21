package com.example.serverprovision.global.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code provision.browse.*} (= filesystem) 보안 properties — DirectoryBrowse 응답 한도 + Files.walk maxDepth.
 *
 * <p>S3.1 (A3) — {@code BrowseSecurityProperties} → {@code FileSystemSecurityProperties} 리네임. {@link #maxDepth} 가
 * Browse 외에도 모든 {@code Files.walk} 호출 (UploadLimitsPolicy / FileSystemHardener / BundleEntrypointDetector /
 * BundleTreeCleanupService) 에서 일관 적용된다. prefix 는 {@code provision.browse.*} 그대로 유지하여
 * 운영자 환경변수 영향을 0 으로.</p>
 */
@ConfigurationProperties(prefix = "provision.browse")
public record FileSystemSecurityProperties(
		/** Browse 응답에 포함할 최대 entry 수. 초과 시 처음 N 개만 + {@code truncated=true}. */
		int maxEntries,
		/** {@code Files.walk} 류 호출의 max depth. 무한 재귀 / 깊은 nested directory 폭주 방어. */
		int maxDepth
) {

}
