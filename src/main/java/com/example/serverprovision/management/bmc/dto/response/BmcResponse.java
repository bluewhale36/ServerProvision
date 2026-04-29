package com.example.serverprovision.management.bmc.dto.response;

import com.example.serverprovision.management.bios.vo.IntegrityStatus;

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
        boolean isDeprecated
) {
}
