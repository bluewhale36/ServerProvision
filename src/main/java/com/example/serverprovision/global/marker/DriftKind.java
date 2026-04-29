package com.example.serverprovision.global.marker;

/**
 * 경로 재조정에서 감지되는 드리프트 종류.
 * <ul>
 *   <li>{@code PATH_DRIFT} — DB 의 path 위치엔 마커 없으나 다른 위치에서 같은 (resourceType, resourceId) 마커 발견. 자동 적용 가능.</li>
 *   <li>{@code MISSING} — DB path 에도 다른 어디에도 마커 없음. 자원 분실 가능성. 관리자 검토.</li>
 *   <li>{@code ORPHAN} — 디스크에 마커는 있지만 DB 에 매칭 자원 없음. 등록 누락 또는 DB 행 삭제 후 잔재.</li>
 *   <li>{@code SIGNATURE_INVALID} — 마커는 있지만 HMAC 서명 깨짐. 변조 의심.</li>
 *   <li>{@code HASH_MISMATCH} — deep scan 시 manifest 해시 불일치. 자원 내용 변조.</li>
 * </ul>
 */
public enum DriftKind {
    PATH_DRIFT,
    MISSING,
    ORPHAN,
    SIGNATURE_INVALID,
    HASH_MISMATCH
}
