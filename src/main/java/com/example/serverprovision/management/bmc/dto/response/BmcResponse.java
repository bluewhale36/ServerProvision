package com.example.serverprovision.management.bmc.dto.response;

import com.example.serverprovision.global.marker.IntegrityStatus;

/**
 * BMC 단건 응답.
 *
 * <p>MK2 — {@code isDeprecated} 추가. 클라이언트는 boolean 조합 없이 본 필드를 직접 렌더한다.</p>
 */
public record BmcResponse(
		Long id,
		Long boardId,
		String name,
		String version,
		String treeRootPath,
		String entrypointRelativePath,
		String manifestHash,
		int fileCount,
		long totalBytes,
		String description,
		IntegrityStatus integrityStatus,
		boolean isEnabled,
		boolean isDeleted,
		boolean isDeprecated,
		// R2-2 — 부모(BoardModel) lifecycle 이 자식 BMC 의 해당 액션을 막는가. 뷰 버튼 disable + tooltip 근거.
		// 서버 가드와 동일한 LifecycleEntity.blocksChildXxx() 결과를 캡처 (UI ↔ 서버 드리프트 0).
		boolean parentBlocksEnable,
		boolean parentBlocksRestore,
		boolean parentBlocksUndeprecate
) {

}
