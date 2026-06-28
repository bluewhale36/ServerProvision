package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUpdateRequest;
import com.example.serverprovision.management.subprogram.dto.response.BoardWithSubprogramListResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MA5 Subprogram 도메인 read + update 코어. Driver / Utility 통합.
 *
 * <p>R6-3 — fat service 5분할. lifecycle 은 {@link SubprogramLifecycleService}, 등록은
 * {@link SubprogramRegistrationService}, marker 발급은 {@link SubprogramMarkerWriter}, 무결성은
 * {@link SubprogramIntegrityService}, 공유 조회 가드는 {@link SubprogramGuards} 로 분리했다. 본 service 에는
 * 조회(findAllGrouped / findByScope / findSubprogram) + 편집(update) + 뷰 변환(toResponse) 만 잔류한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubprogramService {

	private final SubprogramRepository subprogramRepository;
	private final BoardModelRepository boardModelRepository;
	private final EntrypointPolicyService entrypointPolicyService;

	/* ─────────────────────────── 조회 ─────────────────────────── */

	public List<BoardWithSubprogramListResponse> findAllGrouped(SubprogramKind kind, boolean includeDeleted) {
		// 보드별 활성 보드 + 공용 노드(첫 행 고정).
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

		List<Subprogram> all = includeDeleted
				? subprogramRepository.findAllByKind(kind)
				: subprogramRepository.findAllByKindAndIsDeletedFalse(kind);

		// boardId(또는 null) → 자원 목록.
		Map<Long, List<Subprogram>> byBoardId = new HashMap<>();
		List<Subprogram> commonItems = new ArrayList<>();
		for (Subprogram sp : all) {
			if (sp.isCommonScope()) {
				commonItems.add(sp);
			} else {
				byBoardId.computeIfAbsent(sp.getBoardId(), k -> new ArrayList<>()).add(sp);
			}
		}

		List<BoardWithSubprogramListResponse> rows = new ArrayList<>();
		rows.add(BoardWithSubprogramListResponse.common(toResponses(commonItems)));

		for (BoardModel board : boards) {
			List<Subprogram> items = byBoardId.getOrDefault(board.getId(), List.of());
			rows.add(new BoardWithSubprogramListResponse(
					board.getId(),
					board.getVendor(),
					board.getVendor().getDisplayName(),
					board.getModelName(),
					board.isDeleted(),
					toResponses(items)
			));
		}
		return rows;
	}

	public BoardWithSubprogramListResponse findByScope(SubprogramKind kind, BoardScope scope, boolean includeDeleted) {
		List<Subprogram> items;
		if (scope.isCommon()) {
			items = subprogramRepository.findByKindAndCommonScope(kind);
		} else {
			// 보드 활성 검증
			BoardModel board = boardModelRepository.findById(scope.boardId())
					.orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
			items = subprogramRepository.findByKindAndBoardId(kind, scope.boardId());

			List<Subprogram> filtered = items.stream()
					.filter(s -> includeDeleted || !s.isDeleted())
					.toList();
			return new BoardWithSubprogramListResponse(
					board.getId(),
					board.getVendor(),
					board.getVendor().getDisplayName(),
					board.getModelName(),
					board.isDeleted(),
					toResponses(filtered)
			);
		}
		List<Subprogram> filtered = items.stream()
				.filter(s -> includeDeleted || !s.isDeleted())
				.toList();
		return BoardWithSubprogramListResponse.common(toResponses(filtered));
	}

	public SubprogramResponse findSubprogram(Long subprogramId) {
		return toResponse(SubprogramGuards.requireLive(subprogramRepository, subprogramId));
	}

	/* ─────────────────────────── 편집 ─────────────────────────── */

	@Transactional
	public void update(Long subprogramId, SubprogramUpdateRequest request) {
		Subprogram sp = SubprogramGuards.requireLive(subprogramRepository, subprogramId);
		// version 변경 시 (kind, scope, name, version) 중복 재검사
		if (!sp.getVersion().equals(request.version()) || !sp.getName().equals(request.name())) {
			Optional<Subprogram> conflict = sp.isCommonScope()
					? subprogramRepository.findActiveByCommonKey(sp.getKind(), request.name(), request.version())
					: subprogramRepository.findActiveByBoardKey(sp.getKind(), sp.getBoardId(), request.name(), request.version());
			if (conflict.isPresent() && !conflict.get().getId().equals(subprogramId)) {
				BoardScope scope = sp.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(sp.getBoardId());
				throw new DuplicateSubprogramVersionException(sp.getKind(), scope, request.name(), request.version());
			}
		}
		// S3 — entrypoint 입력 검증 (절대경로 / .. / 트리 밖 차단).
		// C2 — 빈 입력(null/blank) 은 "값 유지" 로 해석. 명시적 제거는 별도 액션이 없으므로 wipe 방지.
		// 의미 있는 입력이 들어왔을 때만 정책 검증 후 교체.
		String requestedEntrypoint = request.entrypointRelativePath();
		boolean entrypointProvided = requestedEntrypoint != null && !requestedEntrypoint.isBlank();
		String nextEntrypoint = entrypointProvided
				? entrypointPolicyService.validateAndNormalize(
				Path.of(sp.getTreeRootPath()), requestedEntrypoint)
				: sp.getEntrypointRelativePath();
		sp.update(request.name(), request.version(), request.description(), nextEntrypoint);
	}

	/* ─────────────────────────── 뷰 변환 ─────────────────────────── */

	private List<SubprogramResponse> toResponses(List<Subprogram> entities) {
		return entities.stream()
				.sorted(Comparator.comparing(Subprogram::getName).thenComparing(Comparator.comparing(Subprogram::getVersion).reversed()))
				.map(SubprogramService::toResponse)
				.toList();
	}

	private static SubprogramResponse toResponse(Subprogram entity) {
		BoardModel parent = entity.getBoardModel();   // 공용 자원이면 null → capability 전부 false
		return new SubprogramResponse(
				entity.getId(),
				entity.getKind(),
				entity.getKind().getDisplayName(),
				entity.getBoardId(),
				entity.getName(),
				entity.getVersion(),
				entity.getTreeRootPath(),
				entity.getEntrypointRelativePath(),
				entity.getManifestHash(),
				entity.getFileCount(),
				entity.getTotalBytes(),
				entity.getDescription(),
				entity.getLastIntegrityStatus() != null ? entity.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				entity.isEnabled(),
				entity.isDeleted(),
				entity.isDeprecated(),
				entity.currentStage(),
				// R2-2-1 — 부모(BoardModel) lifecycle capability. 공용 자원(parent=null)은 전부 false. SSOT = BoardModel.blocksChild*().
				parent != null && parent.blocksChildEnable(),
				parent != null && parent.blocksChildUndeprecate(),
				parent != null && parent.blocksChildRestore()
		);
	}
}
