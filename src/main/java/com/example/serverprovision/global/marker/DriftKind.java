package com.example.serverprovision.global.marker;

/**
 * 경로 재조정에서 감지되는 드리프트 종류.
 * <p>MK3 — Trash 패턴 도입으로 lifecycle / FS 정합화. 자동 적용 가능 분기는 운영자 의도가 단일화되는 경우만.
 * 이중 의도 검증 명제 — drift 가 lifecycle + path 두 변화 동시 포함하면 자동 제외.</p>
 *
 * <h3>MK1 (기존)</h3>
 * <ul>
 *   <li>{@code PATH_DRIFT} — DB.path 위치 마커 부재. 다른 active 위치에 (resourceType, resourceId) 마커 발견. 본체도 함께. <b>자동 ON</b></li>
 *   <li>{@code MISSING} — DB.path + 어디에도 매칭 마커 없음. 자원 분실. 자동 OFF</li>
 *   <li>{@code ORPHAN} — 마커는 디스크에 있지만 DB 매칭 자원 없음. 자동 OFF</li>
 *   <li>{@code SIGNATURE_INVALID} — HMAC 서명 깨짐. 변조 의심. 자동 OFF</li>
 *   <li>{@code HASH_MISMATCH} — deep scan 시 manifest 해시 불일치. 자동 OFF</li>
 * </ul>
 *
 * <h3>MK3 신규</h3>
 * <ul>
 *   <li>{@code RESOURCE_RENAMED} — DB.path 위치 마커 부재. 같은 부모 디렉토리 내 다른 파일명으로 자원+마커 동시 발견 (sidecar 1:1 매칭). <b>자동 ON</b></li>
 *   <li>{@code RESOURCE_RENAMED_ORPHAN} — 자원 부재, 같은 디렉토리에 마커는 잔존. 자원 파일명 자동 추론 불가. 자동 OFF</li>
 *   <li>{@code SOFTDEL_ESCAPE_TO_ORIGINAL} — DB.is_deleted=true, trash 부재, active 트리의 DB.iso_path 위치에 자원+마커 발견. <b>자동 ON</b></li>
 *   <li>{@code SOFTDEL_ESCAPE_TO_OTHER} — DB.is_deleted=true, trash 부재, active 트리의 DB.iso_path 외 위치에 자원+마커 발견. 의도 모호. 자동 OFF, 사용자 액션 옵션 미제공</li>
 *   <li>{@code TRASH_LOST} — DB.is_deleted=true, DB.trashed_path 위치 부재. 외부 정리 의심. 자동 OFF</li>
 *   <li>{@code TRASH_MARKER_STALE} — trash 자원 옆에 마커 잔존 (soft-delete 시 정리됐어야 함). <b>자동 ON</b></li>
 * </ul>
 *
 * <h3>MK3-1 신규</h3>
 * <ul>
 *   <li>{@code GHOST_DB_ROW} — DB row 는 소프트삭제 상태이지만 FS 에 자원도 trash 도 없는 dead row.
 *   정의 : {@code is_deleted=true AND trashed_at=null AND trashed_path=null AND Files.notExists(DB.path)}.
 *   default OFF (사용자 사후 검토 가능). drift apply = DB row hard-delete.</li>
 * </ul>
 */
public enum DriftKind {
    PATH_DRIFT,
    MISSING,
    ORPHAN,
    SIGNATURE_INVALID,
    HASH_MISMATCH,
    // MK3 신규
    RESOURCE_RENAMED,
    RESOURCE_RENAMED_ORPHAN,
    SOFTDEL_ESCAPE_TO_ORIGINAL,
    SOFTDEL_ESCAPE_TO_OTHER,
    TRASH_LOST,
    TRASH_MARKER_STALE,
    // MK3-1 신규
    GHOST_DB_ROW
}
