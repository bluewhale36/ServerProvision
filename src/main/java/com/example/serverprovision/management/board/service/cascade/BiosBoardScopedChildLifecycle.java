package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * R3-4 — BoardModel cascade 의 BIOS 자식 어댑터. {@code BoardModelLifecycleService} 의 BIOS 전용
 * 3-블록 복붙을 흡수. {@code @Order(10)} 로 기존 순회 순서(BIOS → BMC → Subprogram) 선두 고정.
 *
 * <p>{@code @Lazy BiosService} — 형제·scanner 를 거친 speculative 순환 차단(기존 BoardModelLifecycleService
 * 의 @Lazy 가 어댑터로 이동, D3). R3-4 본체 rewire(CP4) 후 bootRun 실측에서 순환 0 이면 제거 가능.</p>
 */
@Component
@Order(10)
public class BiosBoardScopedChildLifecycle implements BoardScopedChildLifecycle {

	private final BiosRepository biosRepository;
	private final BiosService biosService;

	public BiosBoardScopedChildLifecycle(
			BiosRepository biosRepository,
			@Lazy BiosService biosService
	) {
		this.biosRepository = biosRepository;
		this.biosService = biosService;
	}

	@Override
	public void recomputeEffective(Long boardId) {
		biosRepository.findAllByBoardModel_IdOrderByVersionDesc(boardId).stream()
				.filter(b -> !b.isDeleted()).forEach(BoardBIOS::recomputeEffective);
	}

	@Override
	public void softDeleteActive(Long boardId) {
		biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(boardId)
				.forEach(bios -> biosService.softDelete(boardId, bios.getId()));
	}

	@Override
	public int restoreDeleted(Long boardId) {
		int restored = 0;
		for (BoardBIOS bios : biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)) {
			biosService.restore(boardId, bios.getId());
			restored++;
		}
		return restored;
	}

	@Override
	public boolean hasAny(Long boardId) {
		return !biosRepository.findAllByBoardModel_IdOrderByVersionDesc(boardId).isEmpty();
	}

	@Override
	public List<String> deletedLabels(Long boardId) {
		List<String> labels = new ArrayList<>();
		biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(bios -> labels.add("BIOS: " + bios.getName()));
		return labels;
	}
}
