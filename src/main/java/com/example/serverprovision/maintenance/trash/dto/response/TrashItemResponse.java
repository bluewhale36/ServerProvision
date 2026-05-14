package com.example.serverprovision.maintenance.trash.dto.response;

import com.example.serverprovision.global.marker.ResourceType;

import java.time.Instant;

/**
 * MK3 — `/maintenance/trash` 페이지의 휴지통 자원 1건 응답.
 *
 * @param resourceType 자원 도메인 (OS_ISO / BIOS_BUNDLE / BMC_FIRMWARE / SUBPROGRAM 등 — Board 는 자체 trash 미적용 검토 필요)
 * @param resourceId   자원 PK
 * @param displayName  사용자 가시 이름 (예: ISO 의 OS이름 + 버전 + 파일명)
 * @param originalPath soft-delete 직전의 원래 경로 (DB.iso_path 등)
 * @param trashedPath  현재 trash 내 절대 경로 (ghost 인 경우 null)
 * @param trashedAt    soft-delete 시각 (ghost 인 경우 null)
 * @param expiresAt    TTL 만료 시각 (trashedAt + 30일 default. 자원별 연장 시 갱신. ghost 인 경우 null)
 * @param ttlWarning   TTL 7일 이내 / 1일 이내일 때 UI 강조용 boolean. ghost 는 항상 false
 * @param ghost        MK3-1 — DB row 는 소프트삭제 상태이지만 FS 자원도 trash 도 없는 dead row.
 *                     true 면 UI 가 "복구 불가" 배지 + 정리 액션만 활성화. 복원 / +30일 연장 비활성.
 * @param childPreview S5-2+ — 메타 자원 (OS_IMAGE / BOARD_MODEL) 의 cascade preview.
 *                     soft-deleted 자식 자원 이름들을 ' · ' 로 join. 자식 없거나 파일 자원이면 null.
 *                     null 이면 cascade 라디오 자체 노출 안 함.
 */
public record TrashItemResponse(
        ResourceType resourceType,
        Long resourceId,
        String displayName,
        String originalPath,
        String trashedPath,
        Instant trashedAt,
        Instant expiresAt,
        boolean ttlWarning,
        boolean ghost,
        String childPreview
) {
}
