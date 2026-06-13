package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;

/**
 * R3-3 — BoardModelService 3분할 시 Metadata / Lifecycle 두 서비스가 공유하는 활성 보드 조회 가드.
 *
 * <p>중복 금지(불가침) — {@code requireActiveBoard} 가 Metadata(findById / update)·
 * Lifecycle(toggle / deprecate / undeprecate / softDelete) 양쪽에서 쓰이므로 정적 헬퍼로 단일화한다.
 * (BIOS R4-3 의 {@code BiosGuards} 와 동일 패턴 — Board 는 가드가 1개뿐이라 최소 형태.)</p>
 */
final class BoardModelGuards {

	private BoardModelGuards() {
	}

	/** 활성(soft-deleted 아님) 보드를 조회하거나 없으면 404. */
	static BoardModel requireActiveBoard(BoardModelRepository repository, Long id) {
		return repository.findByIdAndIsDeletedFalse(id)
				.orElseThrow(() -> new BoardModelNotFoundException(id));
	}
}
