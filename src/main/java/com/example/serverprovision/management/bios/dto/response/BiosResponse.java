package com.example.serverprovision.management.bios.dto.response;

import com.example.serverprovision.management.bios.vo.IntegrityStatus;

/**
 * BIOS 단건 응답 (v3 — 번들 기반).
 * <p>Miller C2/C3 · 편집 폼 프리필 · provisions JSON 엔드포인트에서 공통 사용.
 * {@code integrityStatus} 는 목록 뷰 기본 badge 값이며,
 * 실제 계산 결과 조회는 별도 {@code /integrity-status} 엔드포인트가 담당한다.
 * 전체 목록 렌더마다 모든 트리를 해시하는 비용을 피하기 위함.</p>
 */
public record BiosResponse(
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
		// R2-2 — 부모(BoardModel) lifecycle 이 자식 BIOS 의 해당 액션을 막는가. 뷰 버튼 disable + tooltip 근거.
		// 서버 가드와 동일한 LifecycleEntity.blocksChildXxx() 결과를 캡처 (UI ↔ 서버 드리프트 0).
		boolean parentBlocksEnable,
		boolean parentBlocksRestore,
		boolean parentBlocksUndeprecate
) {

}
