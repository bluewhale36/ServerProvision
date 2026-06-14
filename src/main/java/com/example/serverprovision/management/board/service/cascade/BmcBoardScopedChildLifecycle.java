package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * R3-4 — BoardModel cascade 의 BMC 자식 어댑터. {@code BoardModelLifecycleService} 의 BMC 전용
 * 3-블록 복붙을 흡수. {@code @Order(20)} 로 기존 순회 순서(BIOS → BMC → Subprogram) 중간 고정.
 *
 * <p>{@code @Lazy BmcService} — speculative 순환 차단(D3). 구조는 BIOS 어댑터와 동형(IN_TREE 자식).</p>
 */
@Component
@Order(20)
public class BmcBoardScopedChildLifecycle implements BoardScopedChildLifecycle {

	private final BmcRepository bmcRepository;
	private final BmcService bmcService;

	public BmcBoardScopedChildLifecycle(
			BmcRepository bmcRepository,
			@Lazy BmcService bmcService
	) {
		this.bmcRepository = bmcRepository;
		this.bmcService = bmcService;
	}

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
