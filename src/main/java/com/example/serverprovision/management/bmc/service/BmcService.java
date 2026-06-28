package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.bmc.dto.request.BmcUpdateRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcResponse;
import com.example.serverprovision.management.bmc.dto.response.BoardWithBmcListResponse;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
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
 * R5-3 — BMC 도메인의 read + update 코어. 5분할(Lifecycle / Registration / Integrity / Marker) 후 잔류 책임만 보유한다.
 *
 * <ul>
 *   <li>조회 : Miller 전체 뷰(N+1 방지 배치 조회) + 마지막 검증 스냅샷(lastIntegrityStatus)을 내려간다.</li>
 *   <li>수정 : 메타(name / version / description) 갱신 + (board, version) 중복 검사.</li>
 * </ul>
 *
 * <p>lifecycle 상태 전이는 {@link BmcLifecycleService}, 등록은 {@link BmcRegistrationService},
 * 무결성 검증은 {@link BmcIntegrityService}, marker 발급은 {@link BmcMarkerWriter} 가 담당한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BmcService {

	private final BmcRepository bmcRepository;
	private final BoardModelRepository boardModelRepository;

	// ==== 조회 ========================================================

	public List<BoardWithBmcListResponse> findAllGrouped(boolean includeDeleted) {
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();
		if (boards.isEmpty()) return List.of();

		List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
		List<BoardBMC> allBmc = bmcRepository.findAllByBoardModel_IdIn(boardIds);
		Map<Long, List<BoardBMC>> byBoard = allBmc.stream()
				.filter(b -> includeDeleted || !b.isDeleted())
				.collect(Collectors.groupingBy(b -> b.getBoardModel().getId(), HashMap::new, Collectors.toList()));

		return boards.stream()
				.map(board -> new BoardWithBmcListResponse(
						board.getId(),
						board.getVendor(),
						board.getVendor().getDisplayName(),
						board.getModelName(),
						board.isDeleted(),
						byBoard.getOrDefault(board.getId(), List.of()).stream()
								.sorted(Comparator.comparing(BoardBMC::getVersion).reversed())
								.map(BmcService::toResponse)
								.toList()
				))
				.toList();
	}

	public BmcResponse findBmc(Long boardId, Long bmcId) {
		return toResponse(BmcGuards.requireLiveBmc(bmcRepository, boardModelRepository, boardId, bmcId));
	}

	// ==== 메타 수정 ===================================================

	@Transactional
	public void update(Long boardId, Long bmcId, BmcUpdateRequest request) {
		BoardBMC bmc = BmcGuards.requireLiveBmc(bmcRepository, boardModelRepository, boardId, bmcId);
		if (!bmc.getVersion().equals(request.version())
				&& bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBmcVersionException(boardId, request.version());
		}
		bmc.update(request.name(), request.version(), request.description());
	}

	// ==== Response 변환 ===============================================

	private static BmcResponse toResponse(BoardBMC entity) {
		return new BmcResponse(
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
