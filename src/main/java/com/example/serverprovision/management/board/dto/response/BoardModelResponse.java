package com.example.serverprovision.management.board.dto.response;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;

/**
 * 메인보드 모델 단일 응답. Miller Columns 의 C2 요약 + C3 상세를 한 타입으로 서빙.
 * MK2 — {@code isDeprecated} / {@code lifecycleStage} 노출.
 */
public record BoardModelResponse(
		Long id,
		Vendor vendor,
		String modelName,
		String description,
		int biosCount,
		int bmcCount,
		int subprogramCount,
		boolean isEnabled,
		boolean isDeprecated,
		boolean isDeleted,
		LifecycleStage lifecycleStage,
		/** HF26 — 휴지통 행의 [영구 삭제] 를 막는 사유(서버 가드와 같은 판정). 막지 않거나 활성 행이면 {@code null}. */
		String purgeBlockReason
) {

	public boolean purgeBlocked() {
		return purgeBlockReason != null;
	}

	public static BoardModelResponse of(BoardModel entity) {
		return new BoardModelResponse(
				entity.getId(),
				entity.getVendor(),
				entity.getModelName(),
				entity.getDescription(),
				0,
				0,
				0,
				entity.isEnabled(),
				entity.isDeprecated(),
				entity.isDeleted(),
				LifecycleStage.of(entity.isDeprecated(), entity.isDeleted()),
				null
		);
	}
}
