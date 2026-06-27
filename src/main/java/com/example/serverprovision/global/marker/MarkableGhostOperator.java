package com.example.serverprovision.global.marker;

/**
 * R7-1 — {@code MarkableScanner} 분리 중 <b>ghost row 정리</b> 책임.
 *
 * <p><b>한시적 fail-safe (provisional / quarantine).</b> ghost = DB row 는 삭제 표시인데 실제 파일이 어디에도
 * 없는 dead row. 정리는 파일 이동 없이 {@code repository.delete(row)} 한 줄이라 도메인 service 를 호출하지 않는다
 * (service 역참조 없음). MK3-1 에서 한시적 안전망으로 도입됐고, Ghost 신규 생성 차단이 운영상 확정되면
 * <b>DCM3-2.9 정리 슬라이스에서 본 인터페이스 + 구현 + 주입처({@code SoftDeleteIntentService.forcedClear} 등)를
 * 일괄 제거</b>할 수 있도록 한 곳에 격리한다 (영구 계층인 {@link MarkableInventory}/{@link MarkableTrashOperator} 와 동급 아님).</p>
 *
 * <p>{@code supportedType()} 는 default 의 안내 메시지 합성용으로 재선언한다 — 구현 도메인 scanner 가
 * {@link MarkableInventory#supportedType()} 와 동일 메서드로 한 번에 제공한다.</p>
 */
public interface MarkableGhostOperator {

	ResourceType supportedType();

	/**
	 * MK3-1 — ghost 정리 (DB row hard-delete). reconciliation drift apply 또는 휴지통 clear-ghost
	 * 진입점에서 호출. 도메인 service 의 hard-delete 흐름에 위임 — 자원 / 마커 IO 는 이미 부재이므로
	 * row 삭제만 수행하면 충분.
	 */
	default void applyGhostClear(Long resourceId) {
		throw new UnsupportedOperationException(supportedType() + " 는 ghost 정리를 지원하지 않습니다.");
	}

	/**
	 * MK3-2 (DCM3-2.5) — softDelete reject 의 "강제 정리" 진입점. lifecycle 상태 / FS 자원 존재 여부와
	 * 무관하게 DB row hard-delete 수행. {@link #applyGhostClear} 와 분리한 이유 :
	 * <ul>
	 *   <li>{@code applyGhostClear} 는 GhostEvaluator 검증 통과 (ghost 상태) 만 처리</li>
	 *   <li>{@code applyForcedClear} 는 사용자 명시 액션이므로 검증 없이 처리 — 호출 의도 명확화</li>
	 * </ul>
	 */
	default void applyForcedClear(Long resourceId) {
		throw new UnsupportedOperationException(supportedType() + " 는 forced clear 를 지원하지 않습니다.");
	}
}
