package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.IllegalBmcStateException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;

/**
 * R5-3 — BmcService 5분할 시 분할 클래스들이 공유하는 조회 가드를 정적 헬퍼로 단일화한다.
 *
 * <p>중복 금지(불가침) — {@code requireActiveBoard} 는 {@code BmcRegistrationService}(등록 3경로)·잔류
 * {@code BmcService}(requireLiveBmc 내부)에서, {@code requireLiveBmc} 는 잔류 {@code BmcService}(findBmc/update)·
 * {@code BmcLifecycleService}(toggle/deprecate/undeprecate 등)·{@code BmcIntegrityService} 에서 함께 쓰인다.
 * 분할 클래스마다 복붙하면 드리프트 사고이므로 정적 헬퍼 1곳으로 모은다. ({@code BiosGuards} 선례 동일.)</p>
 *
 * <p>의존성 0 — repository 를 인자로 받는 순수 정적 메서드라 어떤 빈도 주입하지 않는다.</p>
 */
final class BmcGuards {

	private BmcGuards() {
	}

	/** 활성(soft-deleted 아님) 보드를 조회하거나 없으면 404. */
	static BoardModel requireActiveBoard(BoardModelRepository boardModelRepository, Long boardId) {
		return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
	}

	/**
	 * 활성 보드 + 미삭제 BMC 조회. soft-deleted 자원에는 거절(409). 보드 scope 까지 검증해 forging 차단.
	 */
	static BoardBMC requireLiveBmc(
			BmcRepository bmcRepository, BoardModelRepository boardModelRepository,
			Long boardId, Long bmcId
	) {
		requireActiveBoard(boardModelRepository, boardId);
		BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
				.orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
		if (bmc.isDeleted()) {
			throw new IllegalBmcStateException("삭제된 BMC 펌웨어에는 수행할 수 없는 작업입니다. bmcId=" + bmcId);
		}
		return bmc;
	}

	/**
	 * 보드 scope 검증된 BMC 단건을 상태 무관으로 조회. restore / purge 가 soft-deleted 자원에 사용한다.
	 * 활성 부모 board 를 요구하지 않아 ghost catch-22 를 차단한다(join 이 board 연관을 검증).
	 */
	static BoardBMC requireExistingBmc(BmcRepository bmcRepository, Long boardId, Long bmcId) {
		return bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
				.orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
	}
}
