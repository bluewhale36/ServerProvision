package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.lifecycle.DeleteAction;
import com.example.serverprovision.global.lifecycle.LifecycleService;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.global.trash.service.TypedNameGuard;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.exception.IllegalBmcStateException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Map;

/**
 * R5-3 — BMC 의 lifecycle 책임 단일 service. {@link LifecycleService} 구현체.
 *
 * <p>BMC 는 leaf 자원(자식 없음)이므로 interface 의 default {@code restore(Long)} 가 자연 활용되고,
 * {@code restore(Long, boolean cascade)} 의 cascade 인자는 받지만 자식 0 복구 → {@link RestoreResponse#none()}
 * 반환(silent 무시가 아닌 자연 무모순). ({@code BiosLifecycleService} 선례 미러.)</p>
 *
 * <p>R5-3 1-arg 재성형 — 기존 2-arg {@code (boardId, bmcId)} 를 {@link LifecycleService} 시그니처에 맞춰
 * 1-arg {@code (bmcId)} 로 재성형했다. 부모 BoardModel 은 URL boardId 대신 {@code bmc.getBoardModel()} 로
 * 내부 resolve 한다.</p>
 *
 * <p>URL forging 가드 — controller 가 endpoint 진입 시 {@link #assertBelongsToBoard(Long, Long)} 를
 * lifecycle 메서드 호출 전에 호출. URL 의 boardId 와 entity 의 부모 id 일치 검증.</p>
 *
 * <p>typed-name 검증은 static {@link TypedNameGuard#verify}(의존성 0). TypedNameVerifier 빈·MarkableScanner·
 * ObjectProvider 를 주입하지 않아 {@code service→verifier→scanner→service} 순환을 재생성하지 않는다(R7 산출 보존).</p>
 *
 * <p>의존 그래프 — 단방향 : {@code BmcRegistrationService}(REPLACE 시 purge)·{@code BoardBmcMarkableScanner}·
 * {@code BmcBoardScopedChildLifecycle}(cascade)·{@code BmcNudgeService} 가 본 service 를 위임 호출한다.
 * 본 service 는 그들을 역참조하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BmcLifecycleService implements LifecycleService {

	private final BmcRepository bmcRepository;
	private final BoardModelRepository boardModelRepository;
	private final TrashLifecycleService trashLifecycleService;
	private final SoftDeleteIntentService softDeleteIntentService;
	private final BundleTreeCleanupService bundleTreeCleanupService;

	// ==== URL forging 가드 (controller 가 lifecycle 호출 직전에 호출) =========

	/**
	 * URL 의 {@code boardId} path variable 과 BMC entity 의 부모 id 일치 검증.
	 * 사용자가 URL 을 forging 해 다른 보드의 BMC 에 접근 시도하면 차단.
	 *
	 * @throws BmcNotFoundException entity 부재 또는 부모 mismatch
	 */
	public void assertBelongsToBoard(Long bmcId, Long expectedBoardId) {
		BoardBMC bmc = bmcRepository.findById(bmcId)
				.orElseThrow(() -> new BmcNotFoundException(expectedBoardId, bmcId));
		if (!bmc.getBoardModel().getId().equals(expectedBoardId)) {
			throw new BmcNotFoundException(expectedBoardId, bmcId);
		}
	}

	// ==== enabled 토글 =================================================

	/**
	 * S5-2-3-1 — 자식 BMC 단독 toggle. 부모 가드 : 부모 Board 가 비활성/Deprecated/Deleted 면 enable 거절.
	 */
	@Override
	@Transactional
	public void toggleEnabled(Long bmcId) {
		BoardBMC bmc = requireLiveBmc(bmcId);
		boolean nextEnabled = !bmc.isEnabled();
		if (nextEnabled) {
			BoardModel parent = loadParent(bmc);
			String parentState = parent.childEnableBlockReason();   // R2-2 — SSOT (DELETED comprehensive)
			if (parentState != null) {
				throw new ChildLifecycleBlockedByParentException(
						ResourceType.BOARD_MODEL,
						parent.getId(), parentState,
						ResourceType.BMC_FIRMWARE,
						bmcId, "enable",
						parent.displayName()
				);
			}
		}
		bmc.toggleEnabled();
		log.info("[lifecycle.toggle] resource=BMC_FIRMWARE#{} enabled={} outcome=toggled", bmcId, nextEnabled);
	}

	// ==== soft delete / restore ========================================

	/**
	 * MK3 — soft-delete BMC. 도메인 가드 후 공통 trash 흐름 위임. MK3-2 사전조건 검사 포함.
	 */
	@Override
	@Transactional
	public void softDelete(Long bmcId) {
		BoardBMC bmc = requireLiveBmc(bmcId);
		// MK3-2 (DCM3-2.1) — Files.exists 사전조건. flag false 면 통과.
		softDeleteIntentService.checkPrecondition(bmc);
		trashLifecycleService.softDeleteToTrash(bmc);
	}

	/**
	 * MK3-2 (DCM3-2.3 ~ 2.5) — softDelete reject modal 의 두 번째 호출 진입점.
	 * controller 가 token 검증 후 호출. action 에 따라 saga 또는 forced clear 분기.
	 */
	@Transactional
	public void softDeleteWithIntent(Long bmcId, DeleteAction action) {
		switch (action) {
			case CORRECT_PATH_THEN_DELETE -> softDeleteIntentService.reconcileThenDelete(
					ResourceType.BMC_FIRMWARE, bmcId,
					() -> {
						BoardBMC refreshed = requireLiveBmc(bmcId);
						trashLifecycleService.softDeleteToTrash(refreshed);
					}
			);
			case FORCED_CLEAR -> softDeleteIntentService.forcedClear(ResourceType.BMC_FIRMWARE, bmcId);
		}
	}

	/**
	 * HF-1 — {@code restore(Long)} override. {@link LifecycleService} 의 default 위임은 self-invocation 이라
	 * {@code @Transactional} 프록시를 우회한다 → 본 override 가 프록시 진입점이 되어 트랜잭션을 시작한다
	 * ({@code BiosLifecycleService.restore(Long)} 와 동형). controller / scanner 의 단일 인자 restore 호출이
	 * 본 메서드를 거쳐 트랜잭션 경계 안에서 실행되도록 보장.
	 */
	@Override
	@Transactional
	public void restore(Long bmcId) {
		restore(bmcId, false);
	}

	/**
	 * MK3 — restore BMC. S5-2-3-1 부모 가드 : 부모 Board 가 deleted 상태이면 자식 단독 restore 거절.
	 *
	 * <p>BMC 는 leaf 자원 — cascade 인자 받지만 자식 0 복구라 항상 {@link RestoreResponse#none()} 반환.</p>
	 */
	@Override
	@Transactional
	public RestoreResponse restore(Long bmcId, boolean cascade) {
		BoardBMC bmc = requireExistingBmc(bmcId);
		Long boardId = bmc.getBoardModel().getId();
		BoardModel parent = boardModelRepository.findById(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
		if (parent.blocksChildRestore()) {   // R2-2 — SSOT
			throw new ChildLifecycleBlockedByParentException(
					ResourceType.BOARD_MODEL,
					parent.getId(), "DELETED",
					ResourceType.BMC_FIRMWARE,
					bmcId, "restore",
					parent.displayName()
			);
		}
		if (!bmc.isDeleted()) {
			throw new IllegalBmcStateException("이미 활성 상태인 BMC 펌웨어입니다. bmcId=" + bmcId);
		}
		if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, bmc.getVersion())) {
			throw new DuplicateBmcVersionException(boardId, bmc.getVersion());
		}
		trashLifecycleService.restoreFromTrash(
				bmc, bmcEntity -> Map.of(
						"boardId", String.valueOf(boardId),
						"version", bmcEntity.getVersion(),
						"entrypointRelativePath",
						bmcEntity.getEntrypointRelativePath() == null ? "" : bmcEntity.getEntrypointRelativePath()
				)
		);
		return RestoreResponse.none();
	}

	// ==== deprecate / undeprecate ======================================

	/**
	 * MK2 — Active → Deprecated 전이. 엔티티 가드가 SoftDeleted / 이미 Deprecated 케이스를 거절.
	 * 현 시점에 SoftDeleted 자원에는 호출할 수 없으므로 {@code requireLiveBmc} 사용 (삭제됨 → 409).
	 */
	@Override
	@Transactional
	public void deprecate(Long bmcId) {
		requireLiveBmc(bmcId).deprecate();
		log.info("[lifecycle.deprecate] resource=BMC_FIRMWARE#{} outcome=deprecated", bmcId);
	}

	/**
	 * S5-2-3-1 — 자식 BMC 단독 undeprecate. 부모 가드 : 부모 Board 가 deprecated 또는 deleted 상태면 거절.
	 */
	@Override
	@Transactional
	public void undeprecate(Long bmcId) {
		BoardBMC bmc = requireLiveBmc(bmcId);
		BoardModel parent = loadParent(bmc);
		if (parent.blocksChildUndeprecate()) {   // R2-2 — SSOT (DEPRECATED 또는 DELETED)
			throw new ChildLifecycleBlockedByParentException(
					ResourceType.BOARD_MODEL,
					parent.getId(), parent.isDeleted() ? "DELETED" : "DEPRECATED",
					ResourceType.BMC_FIRMWARE,
					bmcId, "undeprecate",
					parent.displayName()
			);
		}
		bmc.undeprecate();
		log.info("[lifecycle.undeprecate] resource=BMC_FIRMWARE#{} outcome=undeprecated", bmcId);
	}

	// ==== purge ========================================================

	/**
	 * S5-2-2 — BMC typed-name 검증 후 영구 삭제. 합성식 : {@code bmc.name}.
	 */
	@Override
	@Transactional
	public void purgeWithTypedNameCheck(Long bmcId, String typedName) {
		BoardBMC bmc = requireExistingBmc(bmcId);
		if (!bmc.isDeleted()) {
			throw new IllegalBmcStateException(
					"영구 삭제는 휴지통(soft-deleted) 상태의 BMC 펌웨어만 가능합니다. bmcId=" + bmcId);
		}
		// R7-2 — 이미 로딩한 엔티티로 검증(재조회·verifier 빈 미사용 → service→verifier 변 소멸).
		TypedNameGuard.verify(bmc, typedName);
		purge(bmcId);
	}

	/**
	 * MK2 — SoftDeleted 자원의 영구 삭제. 트리·marker 물리 삭제 후 DB row 제거.
	 * SoftDeleted 가 아닌 자원에 호출되면 명시적 충돌로 거절 (활성 자원의 우발 영구 삭제 방어).
	 *
	 * <p>soft-deleted BMC purge 는 활성 부모 board 를 요구하지 않는다(ghost catch-22 차단).
	 * {@code requireExistingBmc} 는 보드 scope 만 검증하고, 아래 isDeleted 가드가 활성 자원 우발 삭제를 막는다.</p>
	 */
	@Override
	@Transactional
	public void purge(Long bmcId) {
		BoardBMC bmc = requireExistingBmc(bmcId);
		if (!bmc.isDeleted()) {
			throw new IllegalBmcStateException(
					"영구 삭제는 휴지통(soft-deleted) 상태의 BMC 펌웨어만 가능합니다. bmcId=" + bmcId);
		}
		bundleTreeCleanupService.purgeExistingTree(Path.of(bmc.getTreeRootPath()), "purgeBmc");
		bmcRepository.delete(bmc);
		log.info("[lifecycle.purge] resource=BMC_FIRMWARE#{} outcome=purged", bmcId);
	}

	// ==== private helpers ==============================================

	/**
	 * 미삭제(live) BMC 단건 조회. soft-deleted 자원에는 거절(409). 부모는 entity 관계로 내부 resolve 하므로
	 * boardId 인자 없이 bmcId 만으로 조회한다.
	 */
	private BoardBMC requireLiveBmc(Long bmcId) {
		BoardBMC bmc = bmcRepository.findById(bmcId)
				.orElseThrow(() -> new BmcNotFoundException(null, bmcId));
		if (bmc.isDeleted()) {
			throw new IllegalBmcStateException("삭제된 BMC 펌웨어에는 수행할 수 없는 작업입니다. bmcId=" + bmcId);
		}
		return bmc;
	}

	/**
	 * 상태 무관 단건 조회. restore / purge 가 soft-deleted 자원에 사용한다.
	 */
	private BoardBMC requireExistingBmc(Long bmcId) {
		return bmcRepository.findById(bmcId)
				.orElseThrow(() -> new BmcNotFoundException(null, bmcId));
	}

	/**
	 * 부모 BoardModel 을 entity 관계로 resolve. toggle / undeprecate 의 부모 가드용.
	 */
	private BoardModel loadParent(BoardBMC bmc) {
		Long parentId = bmc.getBoardModel().getId();
		return boardModelRepository.findById(parentId)
				.orElseThrow(() -> new BoardModelNotFoundException(parentId));
	}
}
