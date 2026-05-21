package com.example.serverprovision.global.security.exception;

/**
 * 실행 가능 binary (ELF / PE / Mach-O 등) 가 정책상 거절될 때 (DENY 정책).
 */
public class ExecutableContentRejectedException extends UnsupportedMediaTypeException {

	private static final String GENERIC_MESSAGE = "실행 가능 형식의 파일은 업로드할 수 없습니다.";

	public ExecutableContentRejectedException() {
		super(GENERIC_MESSAGE);
	}

	/**
	 * legacy / 내부 디버깅용. 사용자 응답에는 노아그 권장 (S3.1 B4).
	 */
	public ExecutableContentRejectedException(String filename, String detectedMime) {
		super(GENERIC_MESSAGE);
	}
}
