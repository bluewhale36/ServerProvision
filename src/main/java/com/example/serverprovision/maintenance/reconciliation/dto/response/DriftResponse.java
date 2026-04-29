package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;

import java.time.Instant;

/**
 * 단건 드리프트 응답. {@code Drift} JPA 엔티티를 뷰/REST 에 노출할 때 매핑된다.
 *
 * @param id           DB PK — 단일 admin 환경이라 외부 식별자로 그대로 사용
 * @param resourceType 자원 종류
 * @param resourceId   자원 PK (도메인별)
 * @param kind         드리프트 종류
 * @param oldPath      DB 가 알고 있던 경로 (스캔 시점 기준)
 * @param newPath      재발견된 경로. PATH_DRIFT 일 때만 의미. 그 외엔 null
 * @param detectedAt   드리프트가 감지된 시각
 * @param detail       자유 텍스트 추가 정보. SIGNATURE_INVALID 등에 변조 정황 메시지가 들어간다
 */
public record DriftResponse(
        Long id,
        ResourceType resourceType,
        Long resourceId,
        DriftKind kind,
        String oldPath,
        String newPath,
        Instant detectedAt,
        String detail
) {}
