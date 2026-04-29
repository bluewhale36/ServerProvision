package com.example.serverprovision.management.subprogram.dto.response;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;

/**
 * Subprogram 단건 응답.
 * <p>{@code boardId} 는 공용 자원일 때 {@code null}. {@code entrypointRelativePath} 도 등록 직후엔 {@code null}.</p>
 *
 * <p>MK2 — {@code isDeprecated} / {@code lifecycleStage} 추가. 클라이언트가 boolean 조합으로 stage 어휘를
 * 재계산하지 않도록 서버에서 단일 진입점으로 산출해 내려준다.</p>
 */
public record SubprogramResponse(
        Long id,
        SubprogramKind kind,
        String kindDisplayName,
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
        LifecycleStage lifecycleStage
) {
}
