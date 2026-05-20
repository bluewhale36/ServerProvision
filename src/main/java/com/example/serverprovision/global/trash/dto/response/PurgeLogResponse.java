package com.example.serverprovision.global.trash.dto.response;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeLogDetails;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;

import java.time.Instant;

/**
 * S5-2-4 — purge_log 1행의 응답. {@code /maintenance/trash/purge-log} Pageable 응답에 사용.
 *
 * <p>{@code details} 는 polymorphic sealed type — JSON 직렬화 시 type discriminator 가 포함.</p>
 */
public record PurgeLogResponse(
        Long id,
        ResourceType resourceType,
        Long resourceId,
        String displayName,
        PurgeOrigin origin,
        PurgeOutcome outcome,
        Instant occurredAt,
        Instant purgedAt,
        PurgeLogDetails details
) {
}
