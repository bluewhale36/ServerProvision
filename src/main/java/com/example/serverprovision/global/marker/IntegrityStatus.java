package com.example.serverprovision.global.marker;

import lombok.Getter;

/**
 * 마커 무결성 검증 결과. {@code .provision.json} 마커의 존재 · 서명 · manifestHash 비교 결과를 타입화한다.
 * 도메인 무관(BIOS / BMC / Subprogram / ISO 공용) 마커 개념이라 {@link DriftKind} · {@link ResourceType} 와 함께
 * {@code global/marker} 에 둔다. 목록/상세 렌더링 시 뱃지로 표시되며 {@code /integrity-status} 응답에도 사용된다.
 * <ul>
 *   <li>{@link #ORIGINAL} : marker 존재 + 서명 유효 + manifestHash 일치</li>
 *   <li>{@link #TAMPERED} : marker 존재 + 서명 유효하나 현재 트리 manifestHash 불일치 — 파일이 외부 수정됨</li>
 *   <li>{@link #SIGNATURE_INVALID} : marker 존재하나 HMAC 서명 재계산 불일치 — 다른 서버에서 이식/변조</li>
 *   <li>{@link #MARKER_MISSING} : marker 파일 자체가 없음 — 재발급 필요</li>
 *   <li>{@link #NOT_VERIFIED} : 아직 검증하지 않은 초기 상태 (목록 뷰 기본값)</li>
 * </ul>
 *
 * <p>{@code displayMessage} 는 검증 Job 의 사용자 안내 문구 — R5-2 에서 4 verification launcher 에 복제돼 있던
 * {@code statusMessage} switch 를 enum 단일 소스로 흡수한 것이다(순수 데이터라 생성자 필드).</p>
 */
@Getter
public enum IntegrityStatus {

	ORIGINAL("원본 유지"),
	TAMPERED("변조 감지 (해시 불일치)"),
	SIGNATURE_INVALID("서명 무효"),
	MARKER_MISSING("마커 파일 없음"),
	NOT_VERIFIED("미검증");

	private final String displayMessage;

	IntegrityStatus(String displayMessage) {
		this.displayMessage = displayMessage;
	}

}
