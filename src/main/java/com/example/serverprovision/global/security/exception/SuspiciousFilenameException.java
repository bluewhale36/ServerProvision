package com.example.serverprovision.global.security.exception;

/**
 * 위험 파일명 패턴 ({@code *.lnk}, {@code *.scf} 등) 이 정책상 거절될 때.
 * <p>운영자가 {@code provision.upload.suspicious-filenames-policy = DENY} 로 활성화한 경우에만 발생.</p>
 */
public class SuspiciousFilenameException extends UnsupportedMediaTypeException {

	private static final String GENERIC_MESSAGE = "위험 파일명 패턴이 감지되었습니다.";

	public SuspiciousFilenameException() {
		super(GENERIC_MESSAGE);
	}

	/**
	 * legacy / 내부 디버깅용 (S3.1 B4 — 사용자 응답에는 노아그 권장).
	 */
	public SuspiciousFilenameException(String filename) {
		super(GENERIC_MESSAGE);
	}
}
