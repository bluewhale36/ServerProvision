package com.example.serverprovision.management.bmc.dto.response;

import com.example.serverprovision.management.bios.vo.IntegrityStatus;

/**
 * BMC 단건 응답.
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
        boolean isDeleted
) {
}
