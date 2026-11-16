package com.example.serverprovision.management.common.view;

import com.example.serverprovision.global.marker.IntegrityStatus;

/**
 * 무결성 상태를 badge 렌더링 규칙으로 변환한다.
 */
public final class IntegrityStatusView {

	private IntegrityStatusView() {
	}

	public static String badgeClass(IntegrityStatus status) {
		return (status != null ? status : IntegrityStatus.NOT_VERIFIED).badgeClass();
	}
}
