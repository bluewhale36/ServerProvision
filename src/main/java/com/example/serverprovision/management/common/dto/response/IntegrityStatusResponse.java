package com.example.serverprovision.management.common.dto.response;

import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.common.view.IntegrityStatusView;

import java.time.Instant;

/**
 * 단건 자원의 무결성 상태 조회 응답.
 * CP2 단계에서는 즉시 계산한 상태를 반환하고, 이후 last verified snapshot 이 도입되면
 * 같은 shape 을 그대로 재사용한다.
 */
public record IntegrityStatusResponse(
		Long resourceId,
		IntegrityStatus integrityStatus,
		String badgeClass,
		Instant verifiedAt
) {

	public static IntegrityStatusResponse of(Long resourceId, IntegrityStatus integrityStatus, Instant verifiedAt) {
		return new IntegrityStatusResponse(
				resourceId,
				integrityStatus,
				IntegrityStatusView.badgeClass(integrityStatus),
				verifiedAt
		);
	}
}
