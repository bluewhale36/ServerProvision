package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * R3-4 — BoardModel cascade 의 BIOS 자식 어댑터. {@code BoardModelLifecycleService} 의 BIOS 전용
 * 3-블록 복붙을 흡수. {@code @Order(10)} 로 기존 순회 순서(BIOS → BMC → Subprogram) 선두 고정.
 *
 * <p>{@code BiosService} 는 eager 주입. (구 {@code @Lazy} 는 speculative 과방어였다 — 자식 service 가 Board /
 * cascade 어댑터로 되돌아오는 생성자 의존이 0건이고, 시스템의 진짜 생성자 순환은
 * {@code SoftDeleteIntentService} 의 @Lazy 에서 이미 차단돼 본 cascade 경로를 통과하지 않음이 확인됨.
 * bootRun 실측 순환 0 으로 @Lazy 제거.)</p>
 */
@Component
@RequiredArgsConstructor
@Order(10)
@Slf4j
public class BiosBoardScopedChildLifecycle implements BoardScopedChildLifecycle {

	private final BiosRepository biosRepository;
	private final BiosService biosService;

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
			// ghost(FS 소실 dead row)는 복구 불가 → cascade 에서 건너뜀(부모 restore 가 ghost 한 건에 막히지 않게).
			// 남은 ghost 는 부모 restore 後 휴지통/purge 또는 reconciliation GHOST_DB_ROW 로 정리.
			if (GhostEvaluator.isGhost(bios)) {
				log.warn("[cascade.ghostSkip] resource=BIOS_BUNDLE#{} parent=BOARD_MODEL#{} reason=ghost_fs_lost outcome=skipped",
						bios.getId(), boardId);
				continue;
			}
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
