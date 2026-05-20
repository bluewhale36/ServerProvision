package com.example.serverprovision.global.trash.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * S5-2-4 — purge_log 의 outcome 컬럼 값.
 * SUCCESS 일 때만 purged_at 컬럼이 NOT NULL (사용자 결정 v3-1).
 */
@Getter
@RequiredArgsConstructor
public enum PurgeOutcome {

    SUCCESS("성공"),
    FAILED("실패");

    /** S5-2-4 — UI 표시용 사용자 친화 이름. */
    private final String displayName;
}
