package com.example.serverprovision.management.common.view;

import com.example.serverprovision.global.marker.IntegrityStatus;

/**
 * 무결성 상태를 badge 렌더링 규칙으로 변환한다.
 */
public final class IntegrityStatusView {

	private IntegrityStatusView() {
	}

	public static String badgeClass(IntegrityStatus status) {
		IntegrityStatus safeStatus = status != null ? status : IntegrityStatus.NOT_VERIFIED;
		return switch (safeStatus) {
			case ORIGINAL -> "n-badge-green";
			case TAMPERED -> "n-badge-red";
			case SIGNATURE_INVALID -> "n-badge-orange";
			case MARKER_MISSING, NOT_VERIFIED -> "n-badge-gray";
		};
	}
}
