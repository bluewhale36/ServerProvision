package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.os.entity.ISO;

import java.util.List;

/**
 * ISO 목록/상세 응답 필드.
 * <ul>
 *   <li>{@code extracted} : 추출 파이프라인이 정상 완료된 ISO 여부 (재추출 차단용 플래그).</li>
 *   <li>{@code providedEnvironmentCodes} : 이 ISO 가 제공하는 설치 환경 코드. 아코디언 행 요약 렌더용.</li>
 *   <li>{@code providedPackageGroupCount} : 이 ISO 가 제공하는 패키지 그룹 개수. 개수만 노출.</li>
 *   <li>{@code integrityStatus} : 마커 무결성 badge 기본값. 실제 계산 결과 조회는
 *       별도 {@code /integrity-status} 엔드포인트가 담당한다.</li>
 * </ul>
 * 제공 관계는 LAZY 지만 이 팩토리는 {@code OSImageService} 의 {@code @Transactional} 경계 안에서만 호출되므로
 * 세션이 살아 있는 동안 즉시 조회 가능하다.
 */
public record ISOResponse(
        Long id,
        String isoPath,
        String description,
        boolean isEnabled,
        boolean isDeleted,
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
                entity.isExtractionComplete(),
                envCodes,
                entity.getProvidedPackageGroups().size(),
                entity.getLastIntegrityStatus() != null ? entity.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED
        );
    }
}
