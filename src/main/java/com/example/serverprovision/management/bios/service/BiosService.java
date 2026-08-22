package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.dto.response.BiosResponse;
import com.example.serverprovision.management.bios.dto.response.BoardWithBiosListResponse;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.board.exception.InvalidVersionRankRequestException;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * R4-3 — BIOS 도메인의 read + update 코어. 5분할(Lifecycle / Registration / Integrity / Marker) 후 잔류 책임만 보유한다.
 *
 * <ul>
 *   <li>조회 : Miller 전체 뷰(N+1 방지 배치 조회) + 마지막 검증 스냅샷(lastIntegrityStatus)을 내려간다.</li>
 *   <li>수정 : 메타(name / version / description) 갱신 + (board, version) 중복 검사.</li>
 * </ul>
 *
 * <p>lifecycle 상태 전이는 {@link BiosLifecycleService}, 등록은 {@link BiosRegistrationService},
 * 무결성 검증은 {@link BiosIntegrityService}, marker 발급은 {@link BiosMarkerWriter} 가 담당한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BiosService {

	private final BiosRepository biosRepository;
	private final BoardModelRepository boardModelRepository;

	// ==== 조회 ========================================================

	public List<BoardWithBiosListResponse> findAllGrouped(boolean includeDeleted) {
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();
		if (boards.isEmpty()) return List.of();

		List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
		List<BoardBIOS> allBios = biosRepository.findAllByBoardModel_IdIn(boardIds);
		Map<Long, List<BoardBIOS>> byBoard = allBios.stream()
				.filter(b -> includeDeleted || !b.isDeleted())
				.collect(Collectors.groupingBy(b -> b.getBoardModel().getId(), HashMap::new, Collectors.toList()));

		return boards.stream()
				.map(board -> {
					List<BoardBIOS> ofBoard = byBoard.getOrDefault(board.getId(), List.of()).stream()
							.sorted(Comparator.comparingInt(BoardBIOS::getVersionRank))   // 운영자 순서 SSOT(E2-1-a)
							.toList();
					return new BoardWithBiosListResponse(
							board.getId(),
							board.getVendor(),
							board.getVendor().getDisplayName(),
							board.getModelName(),
							board.isDeleted(),
							latestOf(ofBoard),
							ofBoard.stream().map(BiosService::toResponse).toList()
					);
				})
				.toList();
	}

	/**
	 * "최신" 판정 — 순위 1위인 enabled(effective) 후보(E2-1-a). resolve(E2-1-b)의 LATEST 와 같은 술어라
	 * 화면의 최신 태그 = 실행이 고를 대상이 구조로 일치한다. 없으면 null(후보 0 — 태그 미표시).
	 */
	private static Long latestOf(List<BoardBIOS> rankOrdered) {
		return rankOrdered.stream()
				.filter(b -> !b.isDeleted() && b.isEnabled())
				.findFirst()
				.map(BoardBIOS::getId)
				.orElse(null);
	}

	/**
	 * 버전 순위 재정렬(E2-1-a) — 목록 드래그의 저장 XHR. 요청은 살아있는 행 전부를 원하는 순서로 담고,
	 * 삭제 행은 자기 자리(상대 위치)를 보존한 채 전체를 1..n 으로 밀집 재번호한다.
	 * 타 보드 · 미존재 id 는 404(forging 관례), 누락 · 중복은 400 — 정상 흐름은 드래그가 항상 전체
	 * 목록을 보내므로 둘 다 direct PATCH · stale 화면에서만 발동한다.
	 */
	@Transactional
	public void reorderVersionRanks(Long boardId, List<Long> orderedIds) {
		boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
		List<BoardBIOS> all = biosRepository.findAllByBoardModel_IdOrderByVersionRankAsc(boardId);
		Map<Long, BoardBIOS> live = all.stream()
				.filter(b -> !b.isDeleted())
				.collect(Collectors.toMap(BoardBIOS::getId, b -> b));

		Set<Long> requested = new HashSet<>(orderedIds);
		if (requested.size() != orderedIds.size()) {
			throw InvalidVersionRankRequestException.duplicated();
		}
		for (Long id : orderedIds) {
			if (!live.containsKey(id)) {
				throw new BiosNotFoundException(boardId, id);   // 타 보드 · 미존재 · 삭제 행 — forging 관례 404
			}
		}
		if (requested.size() != live.size()) {
			throw InvalidVersionRankRequestException.incomplete();
		}

		Iterator<Long> next = orderedIds.iterator();
		int rank = 1;
		for (BoardBIOS row : all) {
			if (row.isDeleted()) {
				row.assignVersionRank(rank++);          // 삭제 행 — 상대 위치 보존
			} else {
				live.get(next.next()).assignVersionRank(rank++);
			}
		}
	}

	public BiosResponse findBios(Long boardId, Long biosId) {
		return toResponse(BiosGuards.requireLiveBios(biosRepository, boardModelRepository, boardId, biosId));
	}

	// ==== 메타 수정 ===================================================

	@Transactional
	public void update(Long boardId, Long biosId, BiosUpdateRequest request) {
		BoardBIOS bios = BiosGuards.requireLiveBios(biosRepository, boardModelRepository, boardId, biosId);
		if (!bios.getVersion().equals(request.version())
				&& biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBiosVersionException(boardId, request.version());
		}
		bios.update(request.name(), request.version(), request.description());
	}

	// ==== Response 변환 ===============================================

	private static BiosResponse toResponse(BoardBIOS entity) {
		return new BiosResponse(
				entity.getId(),
				entity.getBoardModel().getId(),
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
				// R2-2 — 부모 BoardModel lifecycle 가드 (엔티티 그래프로 도달, repo 조회 0).
				entity.getBoardModel().blocksChildEnable(),
				entity.getBoardModel().blocksChildRestore(),
				entity.getBoardModel().blocksChildUndeprecate()
		);
	}
}
