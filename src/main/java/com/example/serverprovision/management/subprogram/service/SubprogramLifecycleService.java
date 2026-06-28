package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.lifecycle.DeleteAction;
import com.example.serverprovision.global.lifecycle.LifecycleService;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.global.trash.service.TypedNameGuard;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.IllegalSubprogramStateException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * R6-3 — Subprogram 의 lifecycle 책임 단일 service. {@link LifecycleService} 구현체
 * ({@code BiosLifecycleService} / {@code BmcLifecycleService} 선례 미러).
 *
 * <p>Subprogram 은 leaf 자원(자식 없음)이므로 interface 의 {@code restore(Long)} override 가 트랜잭션 진입점이 되고,
 * {@code restore(Long, boolean cascade)} 의 cascade 인자는 받지만 자식 0 복구 → {@link RestoreResponse#none()}
 * 반환(silent 무시가 아닌 자연 무모순).</p>
 *
 * <p>Subprogram 고유 차이 — 분리 전부터 이미 1-arg {@code (subprogramId)} 라 BIOS / BMC 의 2-arg → 1-arg
 * 재성형이 불필요하다. 부모 BoardModel 은 {@code sp.getBoardModel()} 로 내부 resolve 하며, FK 가 nullable
 * (공용 자원 = boardModel=null)이라 부모 가드는 {@code parent != null} 일 때만 발동한다.</p>
 *
 * <p>URL forging 가드 부재 — Subprogram lifecycle URL 에는 boardId path variable 이 없어
 * (controller 가 {@code /{id}/toggle} 형태로 subprogramId 만 전달) BMC 의 {@code assertBelongsToBoard}
 * 같은 scope 검증 차원이 존재하지 않는다. 분리 전 동작을 그대로 보존한다.</p>
 *
 * <p>typed-name 검증은 static {@link TypedNameGuard#verify}(의존성 0). TypedNameVerifier 빈·MarkableScanner·
 * ObjectProvider 를 주입하지 않아 {@code service→verifier→scanner→service} 순환을 재생성하지 않는다.</p>
 *
 * <p>의존 그래프 — 단방향 : {@code SubprogramRegistrationService}(REPLACE 시 purge)·{@code SubprogramMarkableScanner}·
 * {@code SubprogramBoardScopedChildLifecycle}(cascade)·{@code SubprogramNudgeService} 가 본 service 를 위임
 * 호출한다. 본 service 는 그들을 역참조하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubprogramLifecycleService implements LifecycleService {

	private final SubprogramRepository subprogramRepository;
	private final TrashLifecycleService trashLifecycleService;
	private final SoftDeleteIntentService softDeleteIntentService;
	private final BundleTreeCleanupService bundleTreeCleanupService;

	// ==== enabled 토글 =================================================

	/**
	 * R2-2-1 — 자식 Subprogram 단독 toggle. 활성화(promote) 방향만 부모 가드. 비활성화는 자유.
	 * 공용 자원(parent=null)은 미적용.
	 */
	@Override
	@Transactional
	public void toggleEnabled(Long subprogramId) {
		Subprogram sp = SubprogramGuards.requireLive(subprogramRepository, subprogramId);
		BoardModel parent = sp.getBoardModel();
		if (parent != null && !sp.isEnabled()) {
			String parentState = parent.childEnableBlockReason();   // SSOT (DELETED comprehensive)
			if (parentState != null) {
				throw new ChildLifecycleBlockedByParentException(
						ResourceType.BOARD_MODEL, parent.getId(), parentState,
						ResourceType.SUBPROGRAM, subprogramId, "enable",
						parent.displayName());
			}
		}
		sp.toggleEnabled();
		log.info("[lifecycle.toggle] resource=SUBPROGRAM#{} enabled={} outcome=toggled", sp.getId(), sp.isEnabled());
	}

	// ==== soft delete / restore ========================================

	/**
	 * MK3 — soft-delete Subprogram. 도메인 가드 후 공통 trash 흐름 위임. MK3-2 사전조건 추가.
	 */
	@Override
	@Transactional
	public void softDelete(Long subprogramId) {
		Subprogram sp = SubprogramGuards.requireLive(subprogramRepository, subprogramId);
		// MK3-2 (DCM3-2.1) — Files.exists 사전조건. flag false 면 통과.
		softDeleteIntentService.checkPrecondition(sp);
		trashLifecycleService.softDeleteToTrash(sp);
	}

	/**
	 * MK3-2 (DCM3-2.3 ~ 2.5) — softDelete reject modal 의 두 번째 호출 진입점.
	 */
	@Transactional
	public void softDeleteWithIntent(Long subprogramId, DeleteAction action) {
		switch (action) {
			case CORRECT_PATH_THEN_DELETE -> softDeleteIntentService.reconcileThenDelete(
					ResourceType.SUBPROGRAM, subprogramId,
					() -> {
						Subprogram refreshed = SubprogramGuards.requireLive(subprogramRepository, subprogramId);
						trashLifecycleService.softDeleteToTrash(refreshed);
					}
			);
			case FORCED_CLEAR -> softDeleteIntentService.forcedClear(ResourceType.SUBPROGRAM, subprogramId);
		}
	}

	/**
	 * HF-1 — {@code restore(Long)} override. {@link LifecycleService} 의 default 위임은 self-invocation 이라
	 * {@code @Transactional} 프록시를 우회한다 → 본 override 가 프록시 진입점이 되어 트랜잭션을 시작한다
	 * ({@code BmcLifecycleService.restore(Long)} 와 동형). controller / scanner / cascade 의 단일 인자 restore
	 * 호출이 본 메서드를 거쳐 트랜잭션 경계 안에서 실행되도록 보장.
	 */
	@Override
	@Transactional
	public void restore(Long subprogramId) {
		restore(subprogramId, false);
	}

	/**
	 * MK3 — restore Subprogram. 도메인 가드 (부모 삭제 차단 + 활성 키 충돌) + 공통 흐름.
	 * attributes 는 Subprogram 도메인 메타.
	 *
	 * <p>Subprogram 은 leaf 자원 — cascade 인자 받지만 자식 0 복구라 항상 {@link RestoreResponse#none()} 반환.</p>
	 */
	@Override
	@Transactional
	public RestoreResponse restore(Long subprogramId, boolean cascade) {
		Subprogram sp = SubprogramGuards.requireExisting(subprogramRepository, subprogramId);
		BoardModel parent = sp.getBoardModel();
		// R2-2-1 — 부모 메인보드가 삭제면 자식 단독 restore 거절 (부모부터 복구).
		if (parent != null && parent.blocksChildRestore()) {
			throw new ChildLifecycleBlockedByParentException(
					ResourceType.BOARD_MODEL, parent.getId(), "DELETED",
					ResourceType.SUBPROGRAM, subprogramId, "restore",
					parent.displayName());
		}
		// 동일 활성 키 충돌 가드 — 본 슬라이스는 예외 유지. UI 1차 차단은 R2-2-2(전 도메인 일괄)로 분리.
		Optional<Subprogram> active = sp.isCommonScope()
				? subprogramRepository.findActiveByCommonKey(sp.getKind(), sp.getName(), sp.getVersion())
				: subprogramRepository.findActiveByBoardKey(sp.getKind(), sp.getBoardId(), sp.getName(), sp.getVersion());
		if (active.isPresent()) {
			BoardScope scope = sp.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(sp.getBoardId());
			throw new DuplicateSubprogramVersionException(sp.getKind(), scope, sp.getName(), sp.getVersion());
		}
		trashLifecycleService.restoreFromTrash(
				sp, spEntity -> {
					Map<String, String> attrs = new HashMap<>();
					attrs.put("kind", spEntity.getKind().name());
					attrs.put("name", spEntity.getName());
					attrs.put("version", spEntity.getVersion());
					if (spEntity.getBoardId() != null) {
						attrs.put("boardId", String.valueOf(spEntity.getBoardId()));
					}
					return attrs;
				}
		);
		return RestoreResponse.none();
	}

	// ==== deprecate / undeprecate ======================================

	/**
	 * MK2 — Active → Deprecated 전이. SoftDeleted 자원에는 호출 불가 (LifecycleEntity 가드).
	 */
	@Override
	@Transactional
	public void deprecate(Long subprogramId) {
		Subprogram sp = SubprogramGuards.requireExisting(subprogramRepository, subprogramId);
		sp.deprecate();
		log.info("[lifecycle.deprecate] resource=SUBPROGRAM#{} outcome=deprecated", sp.getId());
	}

	/**
	 * MK2 — Deprecated → Active 전이. R2-2-1 — 부모 메인보드가 Deprecated/삭제면 자식 단독 undeprecate 거절.
	 */
	@Override
	@Transactional
	public void undeprecate(Long subprogramId) {
		Subprogram sp = SubprogramGuards.requireExisting(subprogramRepository, subprogramId);
		BoardModel parent = sp.getBoardModel();
		if (parent != null && parent.blocksChildUndeprecate()) {
			throw new ChildLifecycleBlockedByParentException(
					ResourceType.BOARD_MODEL, parent.getId(),
					parent.isDeleted() ? "DELETED" : "DEPRECATED",
					ResourceType.SUBPROGRAM, subprogramId, "undeprecate",
					parent.displayName());
		}
		sp.undeprecate();   // self-state 가드(!isDeprecated) 안전망 유지
		log.info("[lifecycle.undeprecate] resource=SUBPROGRAM#{} outcome=undeprecated", sp.getId());
	}

	// ==== purge ========================================================

	/**
	 * S5-2-2 — Subprogram typed-name 검증 후 영구 삭제. 합성식 : {@code sp.name}.
	 */
	@Override
	@Transactional
	public void purgeWithTypedNameCheck(Long subprogramId, String typedName) {
		Subprogram sp = SubprogramGuards.requireExisting(subprogramRepository, subprogramId);
		if (!sp.isDeleted()) {
			throw new IllegalSubprogramStateException(
					"활성 상태에서는 영구 삭제할 수 없습니다. 먼저 삭제(soft-delete)를 수행하세요. id=" + subprogramId);
		}
		// 이미 로딩한 엔티티로 검증(재조회·verifier 빈 미사용 → service→verifier 변 소멸).
		TypedNameGuard.verify(sp, typedName);
		purge(subprogramId);
	}

	/**
	 * MK2 — 영구 삭제 (hard-delete). SoftDeleted 자원에 한해 허용. 디스크 트리 + DB row 모두 제거.
	 *
	 * <p>nudge REPLACE 결정의 사후 호출 경로이기도 하다 (별도 트랜잭션). 트리 cleanup 은 Service 책임이며
	 * cleanup 실패 시 트랜잭션 rollback 으로 row 가 보존되어 재시도 가능.</p>
	 */
	@Override
	@Transactional
	public void purge(Long subprogramId) {
		Subprogram sp = SubprogramGuards.requireExisting(subprogramRepository, subprogramId);
		if (!sp.isDeleted()) {
			throw new IllegalSubprogramStateException(
					"활성 상태에서는 영구 삭제할 수 없습니다. 먼저 삭제(soft-delete)를 수행하세요. id=" + subprogramId);
		}
		bundleTreeCleanupService.purgeExistingTree(Path.of(sp.getTreeRootPath()), "purgeSubprogram");
		subprogramRepository.delete(sp);
		log.info("[lifecycle.purge] resource=SUBPROGRAM#{} outcome=purged", sp.getId());
	}
}
