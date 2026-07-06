package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OS 메타 공통 참조 검사 — OS 설치/후처리 1단 inspector 가 공유한다(같은 5줄 복붙 방지).
 */
@Component
@RequiredArgsConstructor
public class OsMetadataReferenceChecker {

    private final OSMetadataRepository osMetadataRepository;

    /** 실존(404) + enabled(409 field-bound, 필드 = osMetadataId) 가드. */
    public OSMetadata requireEnabled(Long osMetadataId) {
        OSMetadata os = osMetadataRepository.findByIdAndIsDeletedFalse(osMetadataId)
                .orElseThrow(() -> new OSMetadataNotFoundException(osMetadataId));
        if (!os.isEnabled()) {
            throw new DisabledResourceReferenceException("osMetadataId",
                    os.getOsName().getDisplayName() + " " + os.getOsVersion());
        }
        return os;
    }

    /** deprecated 사용 서술 — 상세 카드 뱃지 데이터(거절 아님). */
    public Optional<String> describeDeprecated(Long osMetadataId) {
        return osMetadataRepository.findByIdAndIsDeletedFalse(osMetadataId)
                .filter(OSMetadata::isDeprecated)
                .map(os -> os.getOsName().getDisplayName() + " " + os.getOsVersion());
    }
}
