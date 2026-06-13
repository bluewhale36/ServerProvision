package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.dto.response.VendorGroupResponse;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNudgeRequiredException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import lombok.RequiredArgsConstructor;
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
 * R3-3 — BoardModelService 3분할 중 <b>메타 CRUD·조회</b> + 생성 / nudge-payload 빌드 책임.
 *
 * <ul>
 *   <li>findById / findAllGrouped — 조회 (자식 BIOS/BMC/Subprogram 개수 집계 포함).</li>
 *   <li>create / update — 메타 쓰기. create 는 충돌 시 nudge 세션 발급(BoardModelNudgeRequiredException).</li>
 *   <li>completePendingBoardFromNudge / purgeBoardForNudge — nudge confirm 의 실제 자원 생성·교체.
 *       {@code BoardModelNudgeService} 가 세션 orchestration 후 본 서비스로 위임(단방향 의존).</li>
 * </ul>
 *
 * <p>lifecycle 상태 전이는 {@code BoardModelLifecycleService}, 자식 repository 는 집계용으로만 사용.
 * 본 서비스는 다른 board service 에 의존하지 않는다(leaf).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardModelMetadataService {

	private final BoardModelRepository boardModelRepository;
	// 자식 자원 개수 집계(toResponse / findAllGrouped) 전용 — cross-feature read.
	private final BiosRepository biosRepository;
	private final BmcRepository bmcRepository;
	private final SubprogramRepository subprogramRepository;
	private final NudgeRegistry nudgeRegistry;

	// ==== 조회 ========================================================

	public BoardModelResponse findById(Long id) {
		BoardModel board = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
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
				new IntentMetaNudgePayload(
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
	 * {@code BoardModelNudgeService.proceed / replace} 가 세션 orchestration 후 위임.
	 */
	@Transactional
	public Long completePendingBoardFromNudge(NudgeSession session) {
		if (!(session.payload() instanceof IntentMetaNudgePayload payload)) {
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
		BoardModel board = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		// modelName 이 바뀔 때만 동일 (vendor, modelName) 중복 재검증
		if (!board.getModelName().equals(request.modelName())
				&& boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(board.getVendor(), request.modelName())) {
			throw new DuplicateBoardModelException(board.getVendor(), request.modelName());
		}
		board.update(request.modelName(), request.description());
	}

	// ==== 내부 헬퍼 ====================================================

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
				LifecycleStage.of(board.isDeprecated(), board.isDeleted())
		);
	}
}
