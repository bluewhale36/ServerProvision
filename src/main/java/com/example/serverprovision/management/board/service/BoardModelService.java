package com.example.serverprovision.management.board.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.dto.response.VendorGroupResponse;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.BoardModelNudgeRequiredException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A2 페이지의 도메인 로직 총괄.
 * <ul>
 *   <li>Controller 는 Request / Response 만 주고받는다.</li>
 *   <li>엔티티 상태 전이(토글/삭제/복구)는 모두 이 서비스의 도메인 메서드 호출로 수행한다.</li>
 *   <li>A3/A4/A5 합류 뒤에는 {@code softDelete(id)} 가 활성 자식(BIOS/BMC/Driver) 동반 처리를 추가한다.</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BoardModelService {

	private final BoardModelRepository boardModelRepository;
	// A3/A4 합류 : softDelete 시 활성 하위 BIOS / BMC 를 동반 soft 삭제하기 위한 cross-feature 의존.
	// A5 추가 시 DriverRepository 도 함께 주입된다. 3회 반복 확보 후 이벤트 기반으로 리팩터 검토.
	private final BiosRepository biosRepository;
	private final BmcRepository bmcRepository;
	private final NudgeRegistry nudgeRegistry;
	// S5-2-3 — cascade restore 시 자식 BIOS / BMC service 의 restore 로직 재사용.
	// @Lazy : BiosService / BmcService 가 다른 service / scanner 를 거쳐 BoardModelService 를 의존할 경우
	// 발생할 circular reference 차단 (SoftDeleteIntentService 와 동일 패턴).
	private final com.example.serverprovision.management.bios.service.BiosService biosService;
	private final com.example.serverprovision.management.bmc.service.BmcService bmcService;
	// R3-1 — BoardModel cascade 에 board-scoped Subprogram 동반. @Lazy : 형제(bios/bmc)Service 와 동일 circular-ref 차단.
	private final SubprogramRepository subprogramRepository;
	private final com.example.serverprovision.management.subprogram.service.SubprogramService subprogramService;

	public BoardModelService(
			BoardModelRepository boardModelRepository,
			BiosRepository biosRepository,
			BmcRepository bmcRepository,
			NudgeRegistry nudgeRegistry,
			@org.springframework.context.annotation.Lazy
			com.example.serverprovision.management.bios.service.BiosService biosService,
			@org.springframework.context.annotation.Lazy
			com.example.serverprovision.management.bmc.service.BmcService bmcService,
			SubprogramRepository subprogramRepository,
			@org.springframework.context.annotation.Lazy
			com.example.serverprovision.management.subprogram.service.SubprogramService subprogramService
	) {
		this.boardModelRepository = boardModelRepository;
		this.biosRepository = biosRepository;
		this.bmcRepository = bmcRepository;
		this.nudgeRegistry = nudgeRegistry;
		this.biosService = biosService;
		this.bmcService = bmcService;
		this.subprogramRepository = subprogramRepository;
		this.subprogramService = subprogramService;
	}

	// ==== 조회 ========================================================

	public BoardModelResponse findById(Long id) {
		BoardModel board = requireActiveBoard(id);
		int biosCount = biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id).size();
		int bmcCount = bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id).size();
		int subprogramCount = subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(id).size();
		return toResponse(board, biosCount, bmcCount, subprogramCount);
	}

	public List<VendorGroupResponse> findAllGrouped(boolean includeDeleted) {
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

		List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
		Map<Long, Integer> biosCounts = biosRepository.findAllByBoardModel_IdIn(boardIds).stream()
				.filter(bios -> includeDeleted || !bios.isDeleted())
				.collect(Collectors.groupingBy(bios -> bios.getBoardModel().getId(), Collectors.summingInt(__ -> 1)));
		Map<Long, Integer> bmcCounts = bmcRepository.findAllByBoardModel_IdIn(boardIds).stream()
				.filter(bmc -> includeDeleted || !bmc.isDeleted())
				.collect(Collectors.groupingBy(bmc -> bmc.getBoardModel().getId(), Collectors.summingInt(__ -> 1)));
		Map<Long, Integer> subprogramCounts = subprogramRepository.findAllByBoardModel_IdIn(boardIds).stream()
				.filter(sp -> includeDeleted || !sp.isDeleted())
				.collect(Collectors.groupingBy(sp -> sp.getBoardModel().getId(), Collectors.summingInt(__ -> 1)));

		Map<Vendor, List<BoardModel>> byVendor = boards.stream().collect(
				Collectors.groupingBy(BoardModel::getVendor, LinkedHashMap::new, Collectors.toList())
		);

		return byVendor.entrySet().stream()
				.map(entry -> VendorGroupResponse.of(
						entry.getKey(),
						entry.getValue().stream()
								.map(board -> toResponse(
										board,
										biosCounts.getOrDefault(board.getId(), 0),
										bmcCounts.getOrDefault(board.getId(), 0),
										subprogramCounts.getOrDefault(board.getId(), 0)
								))
								.toList()
				))
				.toList();
	}

	// ==== 쓰기 연산 ====================================================

	@Transactional
	public Long create(BoardModelCreateRequest request) {
		// 1) 활성 단순 충돌 — Deprecated 활성 자원이 없을 때만 fail-fast.
		if (boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(request.vendor(), request.modelName())) {
			List<BoardModel> activeDeprecated =
					boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(
							request.vendor(), request.modelName());
			if (activeDeprecated.isEmpty()) {
				throw new DuplicateBoardModelException(request.vendor(), request.modelName());
			}
		}

		// 2) MK2 WAVE 1 — soft-deleted / deprecated 후보 → nudge 세션 발급.
		List<BoardModel> candidates = collectMetaNudgeCandidates(request.vendor(), request.modelName());
		if (!candidates.isEmpty()) {
			throw new BoardModelNudgeRequiredException(buildBoardNudgePayload(request, candidates));
		}

		return persistNewBoard(request.vendor(), request.modelName(), request.description());
	}

	private List<BoardModel> collectMetaNudgeCandidates(Vendor vendor, String modelName) {
		List<BoardModel> softDeleted = boardModelRepository.findAllByVendorAndModelNameAndIsDeletedTrue(vendor, modelName);
		List<BoardModel> deprecated = boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(vendor, modelName);
		List<BoardModel> merged = new ArrayList<>(softDeleted.size() + deprecated.size());
		merged.addAll(softDeleted);
		merged.addAll(deprecated);
		return merged;
	}

	private NudgeRequiredResponse buildBoardNudgePayload(BoardModelCreateRequest request, List<BoardModel> candidates) {
		NudgeSession session = nudgeRegistry.register(
				NudgeResourceType.BOARD_MODEL,
				null,
				candidates.stream().map(BoardModel::getId).toList(),
				new com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload(
						Map.of(
								"modelName", request.modelName(),
								"vendor", request.vendor().name(),
								"description", request.description() != null ? request.description() : ""
						)
				)
		);
		List<NudgeConflictEntry> entries = candidates.stream()
				.map(c -> new NudgeConflictEntry(
						c.getId(),
						LifecycleStage.of(c.isDeprecated(), c.isDeleted()),
						null,
						c.getModelName(),
						c.getVendor().name(),
						Instant.now()
				))
				.toList();
		log.info(
				"[boardModel] nudge required : vendor={}, modelName={}, candidates={}",
				request.vendor(), request.modelName(), candidates.size()
		);
		return NudgeRequiredResponse.of(session.nudgeId(), entries, session.expiresAt());
	}

	private Long persistNewBoard(Vendor vendor, String modelName, String description) {
		BoardModel saved = boardModelRepository.save(BoardModel.builder()
															 .vendor(vendor)
															 .modelName(modelName)
															 .description(description)
															 .build());
		return saved.getId();
	}

	/**
	 * MK2 WAVE 1 — BoardModelNudgeService PROCEED — 충돌 후보 보존, 신규 자원만 ACTIVE 등록.
	 */
	@Transactional
	public Long completePendingBoardFromNudge(NudgeSession session) {
		if (!(session.payload() instanceof com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload payload)) {
			throw new IllegalBoardModelStateException(
					"BoardModel nudge 세션은 IntentMetaNudgePayload 만 허용합니다. nudgeId=" + session.nudgeId());
		}
		Vendor vendor = Vendor.valueOf(payload.attributes().get("vendor"));
		String modelName = payload.attributes().get("modelName");
		if (boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(vendor, modelName)) {
			// race — 다른 트랜잭션이 같은 메타로 활성 자원을 만든 경우.
			List<BoardModel> activeDeprecated =
					boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(vendor, modelName);
			if (activeDeprecated.isEmpty()) {
				throw new DuplicateBoardModelException(vendor, modelName);
			}
		}
		return persistNewBoard(vendor, modelName, payload.attributes().get("description"));
	}

	@Transactional
	public void purgeBoardForNudge(BoardModel target) {
		if (!target.isDeleted() && !target.isDeprecated()) {
			throw new IllegalBoardModelStateException(
					"활성 자원은 nudge replace 대상이 될 수 없습니다. id=" + target.getId());
		}
		boardModelRepository.delete(target);
		log.info(
				"[boardModel] purge for nudge replace : id={}, vendor={}, modelName={}",
				target.getId(), target.getVendor(), target.getModelName()
		);
	}

	@Transactional
	public void update(Long id, BoardModelUpdateRequest request) {
		BoardModel board = requireActiveBoard(id);
		// modelName 이 바뀔 때만 동일 (vendor, modelName) 중복 재검증
		if (!board.getModelName().equals(request.modelName())
				&& boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(board.getVendor(), request.modelName())) {
			throw new DuplicateBoardModelException(board.getVendor(), request.modelName());
		}
		board.update(request.modelName(), request.description());
	}

	/**
	 * R4-1 — Board 활성/비활성 토글 + 자식 effective 재계산 (양방향).
	 * <p>부모 own_enabled flip 후 자식 effective 를 재계산한다. own 은 건드리지 않으므로, 부모 비활성 시
	 * 자식이 강제 비활성되고 부모 활성 시 own_enabled=true 자식만 자동 복원된다 (기존 enable early-return 제거).</p>
	 */
	@Transactional
	public void toggleEnabled(Long id) {
		BoardModel parent = requireActiveBoard(id);
		parent.toggleEnabled();
		cascadeRecompute(id);
	}

	/**
	 * R4-1 — Board deprecate + 자식 effective 재계산.
	 */
	@Transactional
	public void deprecate(Long id) {
		BoardModel parent = requireActiveBoard(id);
		parent.deprecate();
		cascadeRecompute(id);
	}

	/**
	 * R4-1 — Board undeprecate + 자식 effective 재계산.
	 * <p>own 불변 → 운영자가 직접 deprecate 한 자식(own_deprecated=true)은 보존, 부모 추종 자식
	 * (own_deprecated=false)만 활성 복원. 기존 "deprecated 자식 전량 환원" 결함 해소.</p>
	 */
	@Transactional
	public void undeprecate(Long id) {
		BoardModel parent = requireActiveBoard(id);
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
	 * BoardModel soft 삭제. 활성 상태인 하위 BIOS / BMC 도 함께 soft 삭제한다.
	 * 이미 삭제된 자식은 건드리지 않는다 (이전 삭제 시점 보존).
	 * BoardModel 을 복구해도 BIOS / BMC 는 자동 복구되지 않으며 개별적으로 restore 해야 한다.
	 */
	/**
	 * S5-2-3 정합화 — Board soft-delete + 자식 BIOS / BMC 동반 trash 이동.
	 * <p>이전엔 service 가 BIOS/BMC entity 의 softDelete() 직접 호출 → trashLifecycleService 우회 →
	 * ghost 상태. 본 변경에서는 biosService.softDelete / bmcService.softDelete 위임 (정상 trash 이동).</p>
	 */
	@Transactional
	public void softDelete(Long id) {
		BoardModel board = requireActiveBoard(id);
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

	@Transactional
	public void restore(Long id) {
		// 기존 단일 인자 시그니처 — cascade=false 와 동일하게 위임 (호환 보존).
		restore(id, false);
	}

	/**
	 * S5-2-3 — Board restore + 하위 BIOS / BMC 일괄 복구 옵션.
	 *
	 * <p>cascade=true 면 soft-deleted 자식 BIOS / BMC 를 일괄 복구. 각 자식의 restore 는 기존
	 * {@code BiosService.restore} / {@code BmcService.restore} 위임 — duplicate version 검증 +
	 * trashLifecycleService.restoreFromTrash 흐름 그대로 재사용 (중복 코드 회피).</p>
	 *
	 * <p>자식 복구 중 활성 자원과 (boardId, version) 충돌하면 해당 service 가 Duplicate*Exception 던지고
	 * {@code @Transactional} 전체 롤백 — 부모 / 모든 자식 모두 복구 안 된 상태.</p>
	 */
	@Transactional
	public com.example.serverprovision.management.common.dto.response.RestoreResponse restore(Long id, boolean cascade) {
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
			return com.example.serverprovision.management.common.dto.response.RestoreResponse.none();
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
		return new com.example.serverprovision.management.common.dto.response.RestoreResponse(restored);
	}

	/**
	 * S5-2+ — 휴지통 cascade preview 용 — 본 보드에 종속된 soft-deleted BIOS / BMC 이름 라벨.
	 * 호출자 : BoardModelMarkableScanner.findDeletedChildLabels.
	 */
	public List<String> findDeletedChildLabels(Long boardId) {
		java.util.List<String> labels = new java.util.ArrayList<>();
		biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(bios -> labels.add("BIOS: " + bios.getName()));
		bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(bmc -> labels.add("BMC: " + bmc.getName()));
		subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(sp -> labels.add(sp.getKind().getDisplayName() + ": " + sp.getName()));
		return labels;
	}

	/**
	 * S5-2-2 — Board typed-name 검증 후 영구 삭제. Board 는 기존 hard-delete 가 부재하여 본 메서드에서 신설.
	 * 합성식 : {@code vendor.displayName + " " + modelName}.
	 */
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
	 * <p>제약 : 자식 BIOS / BMC 가 한 건이라도 남아 있으면 거절 — 운영자가 자식을 먼저 정리해야 함.</p>
	 */
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

	// ==== 내부 헬퍼 ====================================================

	private BoardModel requireActiveBoard(Long id) {
		return boardModelRepository.findByIdAndIsDeletedFalse(id)
				.orElseThrow(() -> new BoardModelNotFoundException(id));
	}

	private static BoardModelResponse toResponse(BoardModel board, int biosCount, int bmcCount, int subprogramCount) {
		return new BoardModelResponse(
				board.getId(),
				board.getVendor(),
				board.getModelName(),
				board.getDescription(),
				biosCount,
				bmcCount,
				subprogramCount,
				board.isEnabled(),
				board.isDeprecated(),
				board.isDeleted(),
				com.example.serverprovision.global.lifecycle.LifecycleStage.of(board.isDeprecated(), board.isDeleted())
		);
	}
}
