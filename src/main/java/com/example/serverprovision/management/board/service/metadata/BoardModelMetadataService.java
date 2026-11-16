package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.global.lifecycle.dto.ResourceListQuery;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.dto.response.VendorGroupResponse;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.board.service.purge.BoardModelPurgeGuard;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * R3-3 — BoardModelService 3분할 중 <b>메타 CRUD·조회</b> 책임.
 *
 * <ul>
 *   <li>findById / findAllGrouped — 조회 (자식 BIOS/BMC/Subprogram 개수 집계 · 휴지통 행의 영구 삭제 차단 사유 포함).</li>
 *   <li>create / update — 메타 쓰기. (제조사, 모델명) 이 어떤 상태로든 이미 있으면 거절한다(HF26).</li>
 * </ul>
 *
 * <p>HF26 — 등록 중복 확인 창(nudge)을 없앴다. 메인보드는 (제조사, 모델명) 이 정체라 파일 자원처럼 "새 버전" 이 없고,
 * 창의 [그래도 등록] 이 같은 모델을 활성으로 둘 만들거나 [기존 영구 삭제 후 등록] 이 판정 없이 보드를 지웠다.
 * 같은 모델은 복구 · Deprecated 해제 · 영구 삭제 뒤 등록으로 다룬다.</p>
 *
 * <p>lifecycle 상태 전이는 {@code BoardModelLifecycleService}, 자식 repository 는 집계용으로만 사용.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardModelMetadataService {

	private final BoardModelRepository boardModelRepository;
	// 자식 자원 개수 집계(toResponse / findAllGrouped) 전용 — cross-feature read.
	private final BiosRepository biosRepository;
	private final BmcRepository bmcRepository;
	private final SubprogramRepository subprogramRepository;
	private final BoardModelPurgeGuard purgeGuard;

	// ==== 조회 ========================================================

	public BoardModelResponse findById(Long id) {
		BoardModel board = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		int biosCount = biosRepository.findAllByBoardModel_IdAndIsDeletedFalse(id).size();
		int bmcCount = bmcRepository.findAllByBoardModel_IdAndIsDeletedFalse(id).size();
		int subprogramCount = subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(id).size();
		return toResponse(board, biosCount, bmcCount, subprogramCount, null);
	}

	/**
	 * 목록 묶음(S8-3 — 조회 조건 반영). 조건은 보드 모델(C2 항목)에 걸고 묶기 전에 거른다 — 맞는 모델이 없는 제조사는
	 * 묶음이 생기지 않는다. 검색 대상 = 제조사 표시명 · 모델명 · 설명. 목록 밖 호출부(보드 고르기 폼)는
	 * {@link ResourceListQuery#unfiltered(boolean)} 로 부른다.
	 */
	public List<VendorGroupResponse> findAllGrouped(ResourceListQuery query) {
		boolean includeDeleted = query.includeDeleted();
		List<BoardModel> boards = (includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc()).stream()
				.filter(b -> query.matches(b, b.getVendor().getDisplayName(), b.getModelName(), b.getDescription()))
				.toList();

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
										subprogramCounts.getOrDefault(board.getId(), 0),
										// HF26 — 휴지통 행의 [영구 삭제] 사전 차단 사유(서버 가드와 같은 판정). 활성 행은 영구 삭제 버튼이 없다
										board.isDeleted() ? purgeGuard.reasonFor(board.getId()) : null
								))
								.toList()
				))
				.toList();
	}

	// ==== 쓰기 연산 ====================================================

	/**
	 * 등록 — (제조사, 모델명) 이 활성 · Deprecated · 휴지통 어느 상태로든 있으면 거절하고 상태별로 다음 행동을 안내한다(HF26).
	 * DB UNIQUE({@code uk_board_model_vendor_model_name})가 같은 규칙의 안전망이다.
	 */
	@Transactional
	public Long create(BoardModelCreateRequest request) {
		boardModelRepository.findByVendorAndModelName(request.vendor(), request.modelName())
				.ifPresent(existing -> {
					throw DuplicateBoardModelException.againstRegistration(existing);
				});
		BoardModel saved = boardModelRepository.save(BoardModel.builder()
															 .vendor(request.vendor())
															 .modelName(request.modelName())
															 .description(request.description())
															 .build());
		return saved.getId();
	}

	@Transactional
	public void update(Long id, BoardModelUpdateRequest request) {
		BoardModel board = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		// modelName 이 바뀔 때만 동일 (vendor, modelName) 중복 재검증 — 휴지통 행도 정체를 점유한다(HF26, 등록과 같은 규칙).
		// 정상 흐름에서는 수정 폼이 renameConflictReason 으로 먼저 칸 오류를 그리므로, 여기는 레이스 · direct POST 의 안전망이다.
		DuplicateBoardModelException conflict = renameConflict(board, request.modelName());
		if (conflict != null) {
			throw conflict;
		}
		board.update(request.modelName(), request.description());
	}

	/**
	 * 이름 수정 폼의 사전 판정(HF26 CP5 D-2) — {@link #update} 의 서버 가드와 같은 판정을 칸 오류 문구로 돌려준다. 충돌이 없으면 {@code null}.
	 */
	public String renameConflictReason(Long id, String newModelName) {
		BoardModel board = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		DuplicateBoardModelException conflict = renameConflict(board, newModelName);
		return conflict == null ? null : conflict.getMessage();
	}

	// ==== 내부 헬퍼 ====================================================

	/** 이름을 바꿀 때만 같은 (제조사, 모델명) 을 찾는다 — 같은 이름 그대로면 충돌이 아니다. */
	private DuplicateBoardModelException renameConflict(BoardModel board, String newModelName) {
		if (board.getModelName().equals(newModelName)) {
			return null;
		}
		return boardModelRepository.findByVendorAndModelName(board.getVendor(), newModelName)
				.map(DuplicateBoardModelException::againstRename)
				.orElse(null);
	}

	private static BoardModelResponse toResponse(BoardModel board, int biosCount, int bmcCount, int subprogramCount,
												 String purgeBlockReason) {
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
				LifecycleStage.of(board.isDeprecated(), board.isDeleted()),
				purgeBlockReason
		);
	}
}
