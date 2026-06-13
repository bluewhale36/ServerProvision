package com.example.serverprovision.global.orphan.dto;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.entity.OrphanQuarantine;

import java.time.LocalDateTime;

/**
 * 격리된 오펀 자원 1건의 뷰 DTO (durable 복구 목록 / 모달용). 엔티티를 뷰에 직접 노출하지 않는다.
 *
 * <p>R1-4-4 — 도메인 무관화 : {@code osMetadataId} → {@code parentId}, {@code resourceType} 추가.</p>
 *
 * @param failureReason {@code OrphanFailureClass.displayReason()} 의 사용자 표시 문구
 * @param state         {@code OrphanRecoveryState.name()}
 */
public record OrphanQuarantineResponse(
		String recoveryId,
		ResourceType resourceType,
		Long parentId,
		String resolvedPath,
		String originalFilename,
		String failureReason,
		String state,
		LocalDateTime createdAt,
		int retryCount
) {

	public static OrphanQuarantineResponse from(OrphanQuarantine o) {
		return new OrphanQuarantineResponse(
				o.getRecoveryId(),
				o.getResourceType(),
				o.getParentId(),
				o.getResolvedPath(),
				o.getOriginalFilename(),
				o.getFailureClass().displayReason(),
				o.getState().name(),
				o.getCreatedAt(),
				o.getRetryCount()
		);
	}
}
