package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSName;

import java.util.List;

/**
 * OS 이미지 단일 응답. Miller Columns 의 C2 요약 + C3 상세를 한 타입으로 서빙.
 * ISO 리스트는 호출자가 휴지통 모드 여부에 맞춰 미리 필터링해 넘긴다.
 * 환경/패키지 그룹 필드는 A1-1 추출 슬라이스에서 채워지며, 아직 주입되지 않는 호출 경로는 빈 리스트가 들어간다.
 */
public record OSImageResponse(
        Long id,
        OSName osName,
        String osVersion,
        String description,
        boolean isEnabled,
        boolean isDeleted,
        List<ISOResponse> isos,
        List<OSEnvironmentResponse> environments,
        List<OSPackageGroupResponse> packageGroups
) {
    /**
     * 환경·패키지 그룹까지 포함한 풀 응답. A1-1 상세 패널 렌더에 쓰인다.
     */
    public static OSImageResponse of(OSImage entity,
                                     List<ISO> visibleIsos,
                                     List<OSEnvironmentResponse> environments,
                                     List<OSPackageGroupResponse> packageGroups) {
        return new OSImageResponse(
                entity.getId(),
                entity.getOsName(),
                entity.getOsVersion(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.isDeleted(),
                visibleIsos.stream().map(ISOResponse::from).toList(),
                environments,
                packageGroups
        );
    }

    /**
     * 환경·그룹 정보가 필요 없는 호출 경로(폼 프리필 등) 용 기본 팩토리.
     */
    public static OSImageResponse of(OSImage entity, List<ISO> visibleIsos) {
        return of(entity, visibleIsos, List.of(), List.of());
    }
}
