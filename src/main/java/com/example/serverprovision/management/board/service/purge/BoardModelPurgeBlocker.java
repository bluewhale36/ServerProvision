package com.example.serverprovision.management.board.service.purge;

/**
 * 메인보드 영구 삭제를 막는 참조(HF26) — 보드를 따라 휴지통에 가지 않는 다른 도메인의 기록이 구현한다.
 *
 * <p>BIOS · BMC · 드라이버처럼 보드와 함께 soft-delete · 복구되는 자식은 {@code BoardScopedChildLifecycle} 이 맡는다.
 * 서버 하드웨어 기록 · BIOS 세팅 템플릿은 보드를 따라 휴지통에 가지 않으므로 그 계약에 끼우지 않고 이 계약을 따로 둔다.
 * management 는 이 인터페이스만 알고, 구현은 그 기록을 소유한 도메인(execution · provisioning)에 있다.</p>
 */
public interface BoardModelPurgeBlocker {

	/** 이 보드를 참조하는 기록이 남아 있으면 "등록된 서버 2 대" 같은 명사구, 없으면 {@code null}. */
	String remainingReference(Long boardModelId);
}
