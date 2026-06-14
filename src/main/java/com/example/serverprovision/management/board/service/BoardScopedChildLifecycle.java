package com.example.serverprovision.management.board.service;

import java.util.List;

/**
 * R3-4 — BoardModel cascade 의 board-scoped 자식(BIOS / BMC / Subprogram) lifecycle SPI.
 *
 * <p>{@code BoardModelLifecycleService} 의 5 메서드(cascadeRecompute / softDelete / restore / purge /
 * findDeletedChildLabels)에 자식 3종이 복붙되던 블록을, 자식 도메인별 어댑터 구현체의 다형 순회로 통일하기
 * 위한 계약. 호출자는 {@code List<BoardScopedChildLifecycle>} 를 주입받아 순회하므로, 자식 1종 추가 시
 * 분기/줄 확장 없이 어댑터 1개 등록만으로 cascade 에 합류한다(Open/Closed, CLAUDE.md §분기문 legacy 확장 금지).</p>
 *
 * <p>자식 도메인별 시그니처 비대칭(BIOS/BMC service.softDelete(boardId, childId) vs Subprogram
 * service.softDelete(childId))·라벨 포맷 차이는 각 어댑터 구현체 내부로 흡수된다. 순회 순서는 구현체의
 * {@code @Order} 로 고정(BIOS=10 → BMC=20 → Subprogram=30) — 기존 동반 순서·라벨 UI 순서 보존.</p>
 */
public interface BoardScopedChildLifecycle {

	/** 부모 effective 변화 반영 — 해당 보드의 활성 자식 effective(enabled/deprecated) 재계산. */
	void recomputeEffective(Long boardId);

	/** 부모 soft-delete 동반 — 해당 보드의 활성 자식을 trash 이동(자식 service.softDelete 위임). */
	void softDeleteActive(Long boardId);

	/**
	 * 부모 restore 동반 — 해당 보드의 soft-deleted 자식을 일괄 복구.
	 *
	 * @return 복구한 자식 수 (활성 동일키 충돌 시 자식 service 가 예외 → 전체 롤백)
	 */
	int restoreDeleted(Long boardId);

	/** 영구삭제 선결 검사 — 해당 보드에 자식이 한 건이라도 남아 있는가. */
	boolean hasAny(Long boardId);

	/** 휴지통 cascade preview — 해당 보드의 soft-deleted 자식 이름 라벨(도메인별 접두/포맷). */
	List<String> deletedLabels(Long boardId);
}
