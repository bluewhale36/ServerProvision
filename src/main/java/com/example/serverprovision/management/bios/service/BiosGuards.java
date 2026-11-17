package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.exception.IllegalBiosStateException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;

import java.util.Comparator;

/**
 * R4-3 — BiosService 5분할 시 분할 클래스들이 공유하는 조회 가드를 정적 헬퍼로 단일화한다.
 *
 * <p>중복 금지(불가침) — {@code requireActiveBoard} 는 {@code BiosRegistrationService}(등록 3경로)·잔류
 * {@code BiosService}(requireLiveBios 내부)에서, {@code requireLiveBios} 는 잔류 {@code BiosService}(findBios/update)·
 * {@code BiosLifecycleService}(toggle/deprecate/undeprecate 등)에서 함께 쓰인다. 분할 클래스마다 복붙하면
 * 드리프트 사고이므로 정적 헬퍼 1곳으로 모은다. ({@code BoardModelGuards} 선례 동일.)</p>
 *
 * <p>의존성 0 — repository 를 인자로 받는 순수 정적 메서드라 어떤 빈도 주입하지 않는다.</p>
 */
final class BiosGuards {

	private BiosGuards() {
	}

	/** 활성(soft-deleted 아님) 보드를 조회하거나 없으면 404. */
	static BoardModel requireActiveBoard(BoardModelRepository boardModelRepository, Long boardId) {
		return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
	}

	/**
	 * 활성 보드 + 미삭제 BIOS 조회. soft-deleted 자원에는 거절(409). 보드 scope 까지 검증해 forging 차단.
	 */
	static BoardBIOS requireLiveBios(
			BiosRepository biosRepository, BoardModelRepository boardModelRepository,
			Long boardId, Long biosId
	) {
		requireActiveBoard(boardModelRepository, boardId);
		BoardBIOS bios = biosRepository.findByIdAndBoardModel_Id(biosId, boardId)
				.orElseThrow(() -> new BiosNotFoundException(boardId, biosId));
		if (bios.isDeleted()) {
			throw new IllegalBiosStateException("삭제된 BIOS 에는 이 작업을 할 수 없습니다.");
		}
		return bios;
	}

	/**
	 * 같은 (메인보드, 버전)의 살아 있는 BIOS(활성 · Deprecated)가 있으면 그 상태에 맞는 안내로 409(HF30-2). 업로드 전 판정 ·
	 * 창 진행 뒤 재발급 · 등록 본체가 함께 써 기준이 갈라지지 않게 한다. 휴지통 행은 막지 않는다(창이 묻는다).
	 */
	static void assertVersionAvailable(BiosRepository repository, Long boardId, String version) {
		repository.findAllByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, version).stream()
				.min(Comparator.comparing((BoardBIOS existing) -> LifecycleStage.of(existing.isDeprecated(), existing.isDeleted())))
				.ifPresent(existing -> {
					throw DuplicateBiosVersionException.againstRegistration(existing);
				});
	}
}
