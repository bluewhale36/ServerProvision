package com.example.serverprovision.management.board.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.board.service.metadata.BoardModelLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S5-2-3+ — BoardModel 도메인 어댑터. 메타 자원 — 휴지통 노출용 lifecycle 메타만 노출.
 */
@Service
@RequiredArgsConstructor
public class BoardModelMarkableScanner implements MarkableScanner {

	private final BoardModelRepository boardModelRepository;
	// R7-3 — 구 ObjectProvider lazy 주입 → eager 직접 주입. service→verifier 변 소멸(TypedNameGuard)로 순환 부재(bootRun 실측).
	private final BoardModelLifecycleService boardModelService;

	@Override
	public ResourceType supportedType() {
		return ResourceType.BOARD_MODEL;
	}

	@Override
	public List<Markable> findActiveMarkables() {
		return Collections.emptyList();
	}

	@Override
	public Set<Long> findSoftDeletedResourceIds() {
		return Collections.emptySet();
	}

	@Override
	public void applyDriftedPath(Long resourceId, Path newPath) {
		// 메타 자원 — no-op.
	}

	@Override
	public Optional<String> recomputeManifestHash(Markable markable) {
		return Optional.empty();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findTrashed() {
		return boardModelRepository.findAllByIsDeletedTrue().stream()
				.<Markable>map(b -> b)
				.collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Markable> findTrashedById(Long resourceId) {
		return boardModelRepository.findByIdAndIsDeletedTrue(resourceId).<Markable>map(b -> b);
	}

	@Override
	public void restoreFromTrash(Long resourceId, boolean cascade) {
		boardModelService.restore(resourceId, cascade);
	}

	@Override
	public void restoreFromTrash(Long resourceId) {
		boardModelService.restore(resourceId, false);
	}

	/**
	 * 휴지통 영구삭제 — BoardModelLifecycleService.purge 위임 (자식 BIOS / BMC 잔존 시 거절).
	 * R3-3 (D1=B) → R7-3 — ObjectProvider 제거 완료, eager 직접 주입.
	 */
	@Override
	public void purgeFromTrash(Long resourceId) {
		boardModelService.purge(resourceId);
	}

	/**
	 * 휴지통 cascade preview — soft-deleted 자식 BIOS / BMC 이름 list.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<String> findDeletedChildLabels(Long resourceId) {
		return boardModelService.findDeletedChildLabels(resourceId);
	}
}
