package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * R3-4 — BoardModel cascade 의 Subprogram(Driver / Utility) 자식 어댑터.
 * {@code BoardModelLifecycleService} 의 Subprogram 전용 3-블록 복붙을 흡수. {@code @Order(30)} 로 순회 말미 고정.
 *
 * <p>BIOS / BMC 와의 비대칭을 어댑터 내부로 흡수 :
 * <ul>
 *   <li>service 시그니처 — {@code subprogramService.softDelete(spId)} / {@code restore(spId)} (1-arg,
 *       board-scoped 가 아닌 단일 인자). BIOS/BMC 의 (boardId, childId) 2-arg 와 다름.</li>
 *   <li>라벨 포맷 — {@code kind.getDisplayName() + ": " + name} (BIOS/BMC 의 고정 접두 "BIOS: "/"BMC: " 와 다름).</li>
 *   <li>repo 메서드명 — {@code findAllByBoardModel_Id} 계열(ORder/version 무관).</li>
 * </ul>
 * 공용 Subprogram(boardModel=null)은 board.id 매칭에서 자연 제외 → cascade 대상 아님.</p>
 */
@Component
@Order(30)
public class SubprogramBoardScopedChildLifecycle implements BoardScopedChildLifecycle {

	private final SubprogramRepository subprogramRepository;
	private final SubprogramService subprogramService;

	public SubprogramBoardScopedChildLifecycle(
			SubprogramRepository subprogramRepository,
			@Lazy SubprogramService subprogramService
	) {
		this.subprogramRepository = subprogramRepository;
		this.subprogramService = subprogramService;
	}

	@Override
	public void recomputeEffective(Long boardId) {
		subprogramRepository.findAllByBoardModel_Id(boardId).stream()
				.filter(s -> !s.isDeleted()).forEach(Subprogram::recomputeEffective);
	}

	@Override
	public void softDeleteActive(Long boardId) {
		subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(boardId)
				.forEach(sp -> subprogramService.softDelete(sp.getId()));
	}

	@Override
	public int restoreDeleted(Long boardId) {
		int restored = 0;
		for (Subprogram sp : subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)) {
			subprogramService.restore(sp.getId());
			restored++;
		}
		return restored;
	}

	@Override
	public boolean hasAny(Long boardId) {
		return !subprogramRepository.findAllByBoardModel_Id(boardId).isEmpty();
	}

	@Override
	public List<String> deletedLabels(Long boardId) {
		List<String> labels = new ArrayList<>();
		subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(boardId)
				.forEach(sp -> labels.add(sp.getKind().getDisplayName() + ": " + sp.getName()));
		return labels;
	}
}
