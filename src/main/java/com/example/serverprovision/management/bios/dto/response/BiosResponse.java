package com.example.serverprovision.management.bios.dto.response;

import com.example.serverprovision.management.bios.vo.IntegrityStatus;

/**
 * BIOS 단건 응답 (v3 — 번들 기반).
 * <p>Miller C2/C3 · 편집 폼 프리필 · provisions JSON 엔드포인트에서 공통 사용.
 * {@code integrityStatus} 는 목록 뷰에서는 {@link IntegrityStatus#NOT_VERIFIED} 기본값으로 내려가며,
 * 상세 패널의 "지금 검증" 버튼 클릭 시 {@code /verify} 엔드포인트 응답에서만 실제 값이 채워진다.
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
        boolean isDeleted
) {
}
