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
        boolean isDeprecated
) {
}
