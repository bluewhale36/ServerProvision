package com.example.serverprovision.global.security.exception;

/**
 * 정규화된 경로가 {@code provision.path.allowed-roots} 의 어떤 root 의 prefix 도 아닐 때.
 *
 * <p>S3.1 (B4) — 응답 메시지에 절대경로를 그대로 노출하면 path enumeration 통로가 되므로
 * 사용자 응답은 일반화된 메시지만, 상세 path 는 호출 지점의 {@code log.warn} 에서만 노출.</p>
 *
 * <p>S3.2 (K1) — {@code BundleTreeCleanupService.cleanupFailedUpload} 의 catch 분기에서
 * 본 예외는 swallow 하지 않고 re-throw 한다 (가드 발동을 호출자 / 사용자 응답에 도달시킴).</p>
 */
public class PathOutsideAllowedRootsException extends ForbiddenException {

	private static final String GENERIC_MESSAGE = "경로가 허용된 영역 밖에 있습니다.";

	public PathOutsideAllowedRootsException() {
		super(GENERIC_MESSAGE);
	}

	/**
	 * legacy / 내부 디버깅용. 사용자 응답에는 {@link #PathOutsideAllowedRootsException()} 노아그를 권장.
	 */
	public PathOutsideAllowedRootsException(String detail) {
		super(GENERIC_MESSAGE);
	}
}
