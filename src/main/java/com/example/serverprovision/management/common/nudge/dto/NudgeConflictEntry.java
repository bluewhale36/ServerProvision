package com.example.serverprovision.management.common.nudge.dto;

import com.example.serverprovision.global.lifecycle.LifecycleStage;

import java.time.Instant;

/**
 * MK2 — 사용자 modal 에 표시할 충돌 후보 1건의 메타.
 *
 * <p>{@code state} 는 {@link LifecycleStage} 어휘 (ACTIVE / DEPRECATED / SOFT_DELETED). PURGED 자원은
 * row 부재라 conflicts 후보가 될 수 없음.</p>
 */
public record NudgeConflictEntry(
        Long id,
        LifecycleStage state,
        String hash,
        String name,
        String version,
        Instant registeredAt
) {}
