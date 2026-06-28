package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.dto.response.BiosResponse;
import com.example.serverprovision.management.bios.dto.response.BoardWithBiosListResponse;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
				.map(board -> new BoardWithBiosListResponse(
						board.getId(),
						board.getVendor(),
						board.getVendor().getDisplayName(),
						board.getModelName(),
						board.isDeleted(),
						byBoard.getOrDefault(board.getId(), List.of()).stream()
								.sorted(Comparator.comparing(BoardBIOS::getVersion).reversed())
								.map(BiosService::toResponse)
								.toList()
				))
				.toList();
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
