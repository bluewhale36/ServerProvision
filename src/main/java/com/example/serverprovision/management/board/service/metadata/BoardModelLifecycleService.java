package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.global.lifecycle.LifecycleService;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * R3-3 — BoardModelService 3분할 중 <b>lifecycle 상태 전이</b> 책임. {@link LifecycleService} 구현.
 *
 * <ul>
 *   <li>toggle / deprecate / undeprecate — 부모 own flip + 자식 effective 재계산(cascadeRecompute).</li>
 *   <li>softDelete / restore — 자식 BIOS/BMC/Subprogram 동반 trash 이동·복구.</li>
 *   <li>purge / purgeWithTypedNameCheck — 자식 잔존 검사 후 영구 삭제.</li>
 * </ul>
 *
 * <p>R3-4 — 자식 cascade 를 {@link BoardScopedChildLifecycle} 다형 순회로 통일. 기존 자식 BIOS/BMC/Subprogram
 * repository·service 직접 주입(7 필드)을 {@code List<BoardScopedChildLifecycle>} 1 필드로 대체. 자식 1종 추가 시
 * 어댑터 1개 등록만으로 5 메서드(cascadeRecompute / softDelete / restore / purge / findDeletedChildLabels)에
 * 합류(Open/Closed). 순회 순서는 어댑터의 {@code @Order}(BIOS→BMC→Subprogram)로 고정 — 기존 동반 순서·라벨
 * 순서 보존. @Lazy(speculative 순환 차단)는 어댑터로 이동(D3). scanner ObjectProvider 제거는 <b>R7-3</b>.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardModelLifecycleService implements LifecycleService {

	private final BoardModelRepository boardModelRepository;
	// R3-4 — board-scoped 자식(BIOS/BMC/Subprogram) cascade 어댑터. @Order 로 순회 순서 고정.
	private final List<BoardScopedChildLifecycle> children;
	// R3-5 — typed-name 검증 응집점 위임. 휴지통 공통 컴포넌트(displayName SSOT) 재사용.
	private final TypedNameVerifier typedNameVerifier;

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
	 * R3-4 — 자식 3종 복붙을 어댑터 다형 순회로 통일. own 보존, 부모 effective 변화만 반영. 휴지통(soft-deleted)
	 * 자식은 restore 시 개별 재계산되므로 어댑터가 제외. 공용 Subprogram(boardModel=null)은 어댑터 내부에서 자연 제외.
	 */
	private void cascadeRecompute(Long id) {
		children.forEach(child -> child.recomputeEffective(id));
	}

	/**
	 * S5-2-3 정합화 — Board soft-delete + 자식 BIOS / BMC / Subprogram 동반 trash 이동.
	 * <p>R3-4 — 자식 동반 trash 이동을 어댑터 다형 순회로 위임(어댑터가 자식 service.softDelete 호출).
	 * board.softDelete 前에 자식부터(기존 순서 보존). 이미 삭제된 자식은 어댑터가 활성만 선별해 건드리지 않는다.</p>
	 */
	@Override
	@Transactional
	public void softDelete(Long id) {
		BoardModel board = BoardModelGuards.requireActiveBoard(boardModelRepository, id);
		children.forEach(child -> child.softDeleteActive(id));
		// Board 자체 — 메타 자원 lifecycle 메타만 갱신 (자식 동반 이동 後).
		board.softDelete();
		board.markTrashed(null);
	}

	/**
	 * S5-2-3 — Board restore + 하위 BIOS / BMC / Subprogram 일괄 복구 옵션.
	 *
	 * <p>cascade=true 면 soft-deleted 자식을 일괄 복구. R3-4 — 자식 복구를 어댑터 다형 순회로 위임하고 복구 수를
	 * 합산. 각 자식의 restore 는 자식 service 위임(duplicate version 검증 재사용). 자식 복구 중 활성 자원과
	 * 충돌하면 해당 service 가 Duplicate*Exception 던지고 {@code @Transactional} 전체 롤백.</p>
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
		// R3-1 — board-scoped 자식 동반 복구 (board.restore 後 → R2-2-1 부모가드 통과). 활성 동일키 충돌 시 전체 롤백.
		int restored = children.stream().mapToInt(child -> child.restoreDeleted(id)).sum();
		log.info("[restore] BoardModel id={} cascade=true → 하위 자원 {}건 복구", id, restored);
		return new RestoreResponse(restored);
	}

	/**
	 * S5-2+ — 휴지통 cascade preview 용 — 본 보드에 종속된 soft-deleted BIOS / BMC / Subprogram 이름 라벨.
	 * R3-4 — 어댑터 다형 순회로 라벨 수집(도메인별 접두/포맷은 어댑터 내부). 순서는 @Order 고정.
	 * 호출자 : BoardModelMarkableScanner.findDeletedChildLabels. (LifecycleService 인터페이스 외 Board 고유 메서드.)
	 */
	public List<String> findDeletedChildLabels(Long boardId) {
		return children.stream()
				.flatMap(child -> child.deletedLabels(boardId).stream())
				.toList();
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
		typedNameVerifier.verify(ResourceType.BOARD_MODEL, id, typedName);
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
	 * R3-4 — 자식 잔존 검사를 어댑터 다형 순회(anyMatch)로 통일.
	 */
	private void purge(BoardModel board) {
		Long id = board.getId();
		if (children.stream().anyMatch(child -> child.hasAny(id))) {
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
