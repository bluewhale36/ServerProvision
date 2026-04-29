package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.os.entity.ISO;

import java.util.List;

/**
 * ISO 목록/상세 응답 필드.
 *
 * <p>MK2 — {@code isDeprecated} 스칼라 + {@code state} (LifecycleStage 어휘) 추가.
 * 클라이언트가 boolean 조합을 직접 하지 않도록 서버에서 환산해 내려준다.</p>
 */
public record ISOResponse(
        Long id,
        String isoPath,
        String description,
        boolean isEnabled,
        boolean isDeleted,
        boolean isDeprecated,
        LifecycleStage state,
        boolean extracted,
        List<String> providedEnvironmentCodes,
        int providedPackageGroupCount,
        IntegrityStatus integrityStatus
) {
    public static ISOResponse from(ISO entity) {
        List<String> envCodes = entity.getProvidedEnvironments().stream()
                .map(e -> e.getEnvironmentCode().getValue())
                .sorted()
                .toList();
        return new ISOResponse(
                entity.getId(),
                entity.getIsoPath(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.isDeleted(),
                entity.isDeprecated(),
                entity.currentStage(),
                entity.isExtractionComplete(),
                envCodes,
                entity.getProvidedPackageGroups().size(),
                entity.getLastIntegrityStatus() != null ? entity.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED
        );
    }
}
