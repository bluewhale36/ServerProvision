package com.example.serverprovision.management.bios.vo;

/**
 * 번들 무결성 검증 결과. 목록/상세 렌더링 시 뱃지로 표시되며 {@code /verify} 엔드포인트의 응답 값이기도 하다.
 * <ul>
 *   <li>{@link #ORIGINAL} : marker 존재 + 서명 유효 + manifestHash 일치</li>
 *   <li>{@link #TAMPERED} : marker 존재 + 서명 유효하나 현재 트리 manifestHash 불일치 — 파일이 외부 수정됨</li>
 *   <li>{@link #SIGNATURE_INVALID} : marker 존재하나 HMAC 서명 재계산 불일치 — 다른 서버에서 이식/변조</li>
 *   <li>{@link #MARKER_MISSING} : marker 파일 자체가 없음 — 재발급 필요</li>
 *   <li>{@link #NOT_VERIFIED} : 아직 검증하지 않은 초기 상태 (목록 뷰 기본값)</li>
 * </ul>
 */
public enum IntegrityStatus {
    ORIGINAL,
    TAMPERED,
    SIGNATURE_INVALID,
    MARKER_MISSING,
    NOT_VERIFIED
}
