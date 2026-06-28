package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.exception.IllegalSubprogramStateException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNotFoundException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;

/**
 * R6-3 — SubprogramService 5분할 시 분할 클래스들이 공유하는 조회 가드를 정적 헬퍼로 단일화한다
 * ({@code BmcGuards} 선례 동형).
 *
 * <p>중복 금지(불가침) — {@code requireActiveBoard} 는 {@code SubprogramRegistrationService}(등록 3경로)에서,
 * {@code requireLive}(미삭제 단건 + 409 가드) 는 {@code SubprogramLifecycleService}(toggle 등)·잔류
 * {@code SubprogramService}(findSubprogram / update)·{@code SubprogramIntegrityService} 에서, {@code requireExisting}
 * (상태 무관 단건) 은 {@code SubprogramLifecycleService}(restore / deprecate / undeprecate / purge)에서 함께
 * 쓰인다. 분할 클래스마다 복붙하면 드리프트 사고이므로 정적 헬퍼 1곳으로 모은다.</p>
 *
 * <p>BMC 와 다른 점 — Subprogram 의 FK(boardModel)는 nullable(공용 자원). lifecycle URL 에 boardId path
 * variable 이 없어 board-scope forging 차원이 존재하지 않으므로, BMC 의 {@code requireLiveBmc(.., boardId, ..)}
 * 같은 scope 검증 join 이 없다. 단건 조회는 boardId 무관하게 subprogramId 만으로 수행한다(분리 전 동작 보존).</p>
 *
 * <p>의존성 0 — repository 를 인자로 받는 순수 정적 메서드라 어떤 빈도 주입하지 않는다.</p>
 */
final class SubprogramGuards {

	private SubprogramGuards() {
	}

	/** 활성(soft-deleted 아님) 보드를 조회하거나 없으면 404. 공용 scope 등록은 호출하지 않는다(parent=null). */
	static BoardModel requireActiveBoard(BoardModelRepository boardModelRepository, Long boardId) {
		return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
	}

	/**
	 * 미삭제(live) Subprogram 단건 조회. soft-deleted 자원에는 거절(409).
	 * 부모는 entity 관계로 내부 resolve 하므로 boardId 인자 없이 subprogramId 만으로 조회한다.
	 */
	static Subprogram requireLive(SubprogramRepository subprogramRepository, Long subprogramId) {
		Subprogram sp = requireExisting(subprogramRepository, subprogramId);
		if (sp.isDeleted()) {
			throw new IllegalSubprogramStateException("삭제된 자원에는 수행할 수 없는 작업입니다. id=" + subprogramId);
		}
		return sp;
	}

	/**
	 * 상태 무관 단건 조회. restore / deprecate / undeprecate / purge 가 soft-deleted 자원에 사용한다.
	 */
	static Subprogram requireExisting(SubprogramRepository subprogramRepository, Long subprogramId) {
		return subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));
	}
}
