package com.example.serverprovision.global.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code provision.upload.*} 보안 properties.
 * <p>size / count / zip-bomb / executable / suspicious-filename 정책을 한 곳에 모은다.</p>
 */
@ConfigurationProperties(prefix = "provision.upload")
public record UploadSecurityProperties(
		DataSize maxFileSize,
		DataSize maxRequestSize,
		int maxFolderFiles,
		DataSize maxTreeBytes,
		DataSize maxZipUncompressedBytes,
		int zipBombRatio,
		int maxZipEntries,
		ExecutableBinaryPolicy executableBinaryPolicy,
		int executableScanSampleSize,
		SuspiciousFilenamesPolicy suspiciousFilenamesPolicy,
		/**
		 * C6 — multi-user 호스트에서 default {@code java.io.tmpdir} (/tmp) 의 권한 누설을 막기 위한 전용 임시 디렉토리.
		 * <p>{@code spring.servlet.multipart.location} 과 동일 값으로 권장. {@code null}/blank 면 default tmpdir 로 fallback.</p>
		 */
		String tempDir,
		/**
		 * C7 — stored entry (compressed=0) 의 단일 entry size 임계 (bytes). 0 이하면 검사 skip.
		 */
		DataSize maxStoredEntryBytes
) {

	public enum ExecutableBinaryPolicy {
		/**
		 * 실행 가능 binary 가 감지되면 거절 (default).
		 */
		DENY,
		/**
		 * 통과시키되 warning 로그 + intent warnings 추가.
		 */
		WARN,
		/**
		 * 모든 콘텐츠 통과 (예: BIOS firmware 가 ELF 형식인 운영 환경).
		 */
		ALLOW
	}


	public enum SuspiciousFilenamesPolicy {
		/**
		 * 검사 안 함 (default).
		 */
		DISABLED,
		WARN,
		DENY
	}
}
