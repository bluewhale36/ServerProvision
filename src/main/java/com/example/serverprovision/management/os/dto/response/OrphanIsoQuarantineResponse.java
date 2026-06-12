package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.os.entity.OrphanIsoQuarantine;

import java.time.LocalDateTime;

/**
 * 격리된 오펀 ISO 1건의 뷰 DTO (durable 복구 목록 / 모달용). 엔티티를 뷰에 직접 노출하지 않는다.
 */
public record OrphanIsoQuarantineResponse(
		String recoveryId,
		Long osMetadataId,
		String resolvedPath,
		String originalFilename,
		String failureReason,
		String state,
		LocalDateTime createdAt,
		int retryCount
) {

	public static OrphanIsoQuarantineResponse from(OrphanIsoQuarantine o) {
		return new OrphanIsoQuarantineResponse(
				o.getRecoveryId(),
				o.getOsMetadataId(),
				o.getResolvedPath(),
				o.getOriginalFilename(),
				o.getFailureClass().displayReason(),
				o.getState().name(),
				o.getCreatedAt(),
				o.getRetryCount()
		);
	}
}
