package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * R3-4 — BoardModel cascade 의 BMC 자식 어댑터. {@code BoardModelLifecycleService} 의 BMC 전용
 * 3-블록 복붙을 흡수. {@code @Order(20)} 로 기존 순회 순서(BIOS → BMC → Subprogram) 중간 고정.
 *
 * <p>{@code BmcService} 는 eager 주입 — 구 {@code @Lazy}(speculative) 제거. 사유는 BIOS 어댑터와 동일
 * (cascade 경로에 진짜 순환 없음, 실측 bootRun 순환 0). 구조는 BIOS 어댑터와 동형(IN_TREE 자식).</p>
 */
@Component
@RequiredArgsConstructor
@Order(20)
@Slf4j
public class BmcBoardScopedChildLifecycle implements BoardScopedChildLifecycle {

	private final BmcRepository bmcRepository;
	private final BmcService bmcService;

	@Override
	public void recomputeEffective(Long boardId) {
		bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(boardId).stream()
				.filter(b -> !b.isDeleted()).forEach(BoardBMC::recomputeEffective);
	}

	@Override
	public void softDeleteActive(Long boardId) {
		bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(boardId)
				.forEach(bmc -> bmcService.softDelete(boardId, bmc.getId()));
	}

	@Override
	public int restoreDeleted(Long boardId) {
		int restored = 0;
		for (BoardBMC bmc : bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)) {
			// ghost(FS 소실 dead row)는 복구 불가 → cascade 에서 건너뜀(부모 restore 차단 방지). 정리는 휴지통/purge.
			if (GhostEvaluator.isGhost(bmc)) {
				log.warn("[cascade.ghostSkip] resource=BMC_FIRMWARE#{} parent=BOARD_MODEL#{} reason=ghost_fs_lost outcome=skipped",
						bmc.getId(), boardId);
				continue;
			}
			bmcService.restore(boardId, bmc.getId());
			restored++;
		}
		return restored;
	}

	@Override
	public boolean hasAny(Long boardId) {
		return !bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(boardId).isEmpty();
	}

	@Override
	public List<String> deletedLabels(Long boardId) {
		List<String> labels = new ArrayList<>();
		bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(bmc -> labels.add("BMC: " + bmc.getName()));
		return labels;
	}
}
