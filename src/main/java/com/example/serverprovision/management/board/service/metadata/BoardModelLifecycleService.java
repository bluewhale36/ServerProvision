package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.LifecycleService;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * R3-3 — BoardModelService 3분할 중 <b>lifecycle 상태 전이</b> 책임. {@link LifecycleService} 구현.
 *
 * <ul>
 *   <li>toggle / deprecate / undeprecate — 부모 own flip + 자식 effective 재계산(cascadeRecompute).</li>
 *   <li>softDelete / restore — 자식 BIOS/BMC/Subprogram 동반 trash 이동·복구(자식 service 위임).</li>
 *   <li>purge / purgeWithTypedNameCheck — 자식 잔존 검사 후 영구 삭제.</li>
 * </ul>
 *
 * <p>자식 service 는 cascade 재사용 위해 {@code @Lazy} 직접 주입 — 형제 service / scanner 를 거쳐
 * 본 service 를 의존할 경우의 circular-ref 차단(기존 BoardModelService 와 동일). 다형 순회 전환은 <b>R3-4</b>,
 * scanner 의 ObjectProvider 제거(4-sealed SPI)는 <b>R7-3</b>.</p>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BoardModelLifecycleService implements LifecycleService {

	private final BoardModelRepository boardModelRepository;
	private final BiosRepository biosRepository;
	private final BmcRepository bmcRepository;
	private final SubprogramRepository subprogramRepository;
	private final BiosService biosService;
	private final BmcService bmcService;
	private final SubprogramService subprogramService;

	public BoardModelLifecycleService(
			BoardModelRepository boardModelRepository,
			BiosRepository biosRepository,
			BmcRepository bmcRepository,
			SubprogramRepository subprogramRepository,
			@Lazy BiosService biosService,
			@Lazy BmcService bmcService,
			@Lazy SubprogramService subprogramService
	) {
		this.boardModelRepository = boardModelRepository;
		this.biosRepository = biosRepository;
		this.bmcRepository = bmcRepository;
		this.subprogramRepository = subprogramRepository;
		this.biosService = biosService;
		this.bmcService = bmcService;
		this.subprogramService = subprogramService;
	}

	/**
	 * R4-1 — Board 활성/비활성 토글 + 자식 effective 재계산 (양방향).
	 * <p>부모 own_enabled flip 후 자식 effective 를 재계산한다. own 은 건드리지 않으므로, 부모 비활성 시
	 * 자식이 강제 비활성되고 부모 활성 시 own_enabled=true 자식만 자동 복원된다 (기존 enable early-return 제거).</p>
	 */
	@Override
	@Transactional
	public void toggleEnabled(Long id) {
		BoardModel parent = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		parent.toggleEnabled();
		cascadeRecompute(id);
	}

	/**
	 * R4-1 — Board deprecate + 자식 effective 재계산.
	 */
	@Override
	@Transactional
	public void deprecate(Long id) {
		BoardModel parent = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		parent.deprecate();
		cascadeRecompute(id);
	}

	/**
	 * R4-1 — Board undeprecate + 자식 effective 재계산.
	 * <p>own 불변 → 운영자가 직접 deprecate 한 자식(own_deprecated=true)은 보존, 부모 추종 자식
	 * (own_deprecated=false)만 활성 복원. 기존 "deprecated 자식 전량 환원" 결함 해소.</p>
	 */
	@Override
	@Transactional
	public void undeprecate(Long id) {
		BoardModel parent = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		parent.undeprecate();
		cascadeRecompute(id);
	}

	/**
	 * R4-1 — board-scoped 자식(BIOS / BMC / Subprogram) 의 effective(is_enabled/is_deprecated) 재계산.
	 * own 보존, 부모 effective 변화만 반영. 휴지통(soft-deleted) 자식은 restore 시 개별 재계산되므로 제외.
	 * 공용 Subprogram(boardModel=null) 은 boardModel.id 매칭에서 자연 제외 → effective=own 유지.
	 */
	private void cascadeRecompute(Long id) {
		biosRepository.findAllByBoardModel_IdOrderByVersionDesc(id).stream()
				.filter(b -> !b.isDeleted()).forEach(BoardBIOS::recomputeEffective);
		bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(id).stream()
				.filter(b -> !b.isDeleted()).forEach(BoardBMC::recomputeEffective);
		subprogramRepository.findAllByBoardModel_Id(id).stream()
				.filter(s -> !s.isDeleted()).forEach(Subprogram::recomputeEffective);
	}

	/**
	 * S5-2-3 정합화 — Board soft-delete + 자식 BIOS / BMC / Subprogram 동반 trash 이동.
	 * <p>service.softDelete 위임 (trash 이동 + DB 갱신). 이미 삭제된 자식은 건드리지 않는다.</p>
	 */
	@Override
	@Transactional
	public void softDelete(Long id) {
		BoardModel board = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		// 자식 BIOS / BMC 활성 자원 동반 trash 이동 — service.softDelete 위임 (trash 이동 + DB 갱신).
		biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id)
				.forEach(bios -> biosService.softDelete(id, bios.getId()));
		bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id)
				.forEach(bmc -> bmcService.softDelete(id, bmc.getId()));
		// R3-1 — board-scoped Subprogram 동반 trash 이동 (단일 인자 service 위임, board.softDelete 前).
		subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(id)
				.forEach(sp -> subprogramService.softDelete(sp.getId()));
		// Board 자체 — 메타 자원 lifecycle 메타만 갱신.
		board.softDelete();
		board.markTrashed(null);
	}

	@Override
	@Transactional
	public void restore(Long id) {
		// 기존 단일 인자 시그니처 — cascade=false 와 동일하게 위임 (호환 보존).
		// R3-5 에서 LifecycleService default 흡수로 본 명시 오버로드 제거 예정.
		restore(id, false);
	}

	/**
	 * S5-2-3 — Board restore + 하위 BIOS / BMC / Subprogram 일괄 복구 옵션.
	 *
	 * <p>cascade=true 면 soft-deleted 자식을 일괄 복구. 각 자식의 restore 는 기존 {@code *Service.restore}
	 * 위임 — duplicate version 검증 + trashLifecycleService.restoreFromTrash 흐름 재사용. 자식 복구 중 활성
	 * 자원과 충돌하면 해당 service 가 Duplicate*Exception 던지고 {@code @Transactional} 전체 롤백.</p>
	 */
	@Override
	@Transactional
	public RestoreResponse restore(Long id, boolean cascade) {
		BoardModel board = boardModelRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalBoardModelStateException(
						"이미 활성 상태이거나 존재하지 않는 메인보드 모델입니다. id=" + id));
		// 복구하려는 (vendor, modelName) 조합이 이미 활성으로 존재하면 충돌
		if (boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(board.getVendor(), board.getModelName())) {
			throw new DuplicateBoardModelException(board.getVendor(), board.getModelName());
		}
		board.restore();
		board.clearTrashed();  // S5-2-3 — 메타 자원 lifecycle 메타 초기화.
		if (!cascade) {
			return RestoreResponse.none();
		}
		int restored = 0;
		for (BoardBIOS bios : biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(id)) {
			biosService.restore(id, bios.getId());
			restored++;
		}
		for (BoardBMC bmc : bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(id)) {
			bmcService.restore(id, bmc.getId());
			restored++;
		}
		// R3-1 — board-scoped Subprogram 동반 복구 (board.restore 後 → R2-2-1 부모가드 통과). 활성 동일키 충돌 시 전체 롤백.
		for (Subprogram sp : subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(id)) {
			subprogramService.restore(sp.getId());
			restored++;
		}
		log.info("[restore] BoardModel id={} cascade=true → 하위 자원 {}건 복구", id, restored);
		return new RestoreResponse(restored);
	}

	/**
	 * S5-2+ — 휴지통 cascade preview 용 — 본 보드에 종속된 soft-deleted BIOS / BMC / Subprogram 이름 라벨.
	 * 호출자 : BoardModelMarkableScanner.findDeletedChildLabels. (LifecycleService 인터페이스 외 Board 고유 메서드.)
	 */
	public List<String> findDeletedChildLabels(Long boardId) {
		List<String> labels = new ArrayList<>();
		biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(bios -> labels.add("BIOS: " + bios.getName()));
		bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(bmc -> labels.add("BMC: " + bmc.getName()));
		subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(sp -> labels.add(sp.getKind().getDisplayName() + ": " + sp.getName()));
		return labels;
	}

	/**
	 * S5-2-2 — Board typed-name 검증 후 영구 삭제. 합성식 : {@code vendor.displayName + " " + modelName}.
	 */
	@Override
	@Transactional
	public void purgeWithTypedNameCheck(Long id, String typedName) {
		BoardModel board = boardModelRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalBoardModelStateException(
						"soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. id=" + id));
		String expected = board.displayName();
		if (!expected.equals(typedName)) {
			throw new TypedNameMismatchException(expected, typedName);
		}
		purge(board);
	}

	/**
	 * S5-2+ — 휴지통 직진입 영구 삭제 (typed-name 검증 우회). 휴지통 페이지에서 호출.
	 * <p>제약 : 자식 BIOS / BMC / Subprogram 이 한 건이라도 남아 있으면 거절.</p>
	 */
	@Override
	@Transactional
	public void purge(Long id) {
		BoardModel board = boardModelRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalBoardModelStateException(
						"soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. id=" + id));
		purge(board);
	}

	/**
	 * 공통 hard-delete 본체 — 자식 잔존 검사 + DB row 제거.
	 */
	private void purge(BoardModel board) {
		Long id = board.getId();
		boolean hasBios = !biosRepository.findAllByBoardModel_IdOrderByVersionDesc(id).isEmpty();
		boolean hasBmc = !bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(id).isEmpty();
		boolean hasSubprogram = !subprogramRepository.findAllByBoardModel_Id(id).isEmpty();
		if (hasBios || hasBmc || hasSubprogram) {
			throw new IllegalBoardModelStateException(
					"자식 BIOS / BMC / Subprogram(드라이버·유틸리티) 자원이 남아 있어 메인보드 모델을 영구 삭제할 수 없습니다. "
							+ "자식을 먼저 모두 영구 삭제해주세요. id=" + id);
		}
		boardModelRepository.delete(board);
		log.info(
				"[purge] BoardModel 영구 삭제. id={}, vendor={}, modelName={}",
				id, board.getVendor(), board.getModelName()
		);
	}
}
