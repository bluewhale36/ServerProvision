package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 업로드 / 등록 대상 경로의 파일시스템에 ISO 를 저장할 가용 공간이 부족.
 * Intent 단계에서 즉시 거절하여 13GB 짜리 multipart 전송이 시작되지 않게 한다. → 409
 */
public class InsufficientDiskSpaceException extends ConflictException {

	public InsufficientDiskSpaceException(String path, long required, long available) {
		super(String.format(
				"디스크 공간이 부족합니다. path=%s, 필요=%s, 가용=%s",
				path, formatBytes(required), formatBytes(available)
		));
	}

	private static String formatBytes(long bytes) {
		if (bytes < 0) return "?";
		if (bytes < 1024) return bytes + " B";
		long kb = bytes / 1024;
		if (kb < 1024) return kb + " KB";
		long mb = kb / 1024;
		if (mb < 1024) return mb + " MB";
		long gb = mb / 1024;
		return gb + " GB";
	}
}
