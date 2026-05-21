package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.global.trash.exception.GhostClearTargetNotGhostException;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SUBPROGRAM (Driver + Utility) 도메인 어댑터.
 * <p>두 kind 가 단일 엔티티에 통합되어 있어 한 Scanner 로 모두 흡수된다 (kind 별 분리 스캔 불필요).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubprogramMarkableScanner implements MarkableScanner {

	private final SubprogramRepository subprogramRepository;
	private final BundleManifestService bundleManifestService;
	private final SubprogramService subprogramService;

	@Override
	public ResourceType supportedType() {
		return ResourceType.SUBPROGRAM;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findActiveMarkables() {
		return subprogramRepository.findAllByIsDeletedFalse().stream()
				.<Markable>map(s -> s)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Long> findSoftDeletedResourceIds() {
		return subprogramRepository.findIdsByIsDeletedTrue();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Markable> findActiveMarkableById(Long resourceId) {
		return subprogramRepository.findById(resourceId)
				.filter(s -> !s.isDeleted())
				.map(s -> s);
	}

	@Override
	@Transactional
	public void applyDriftedPath(Long resourceId, Path newPath) {
		Subprogram subprogram = subprogramRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("Subprogram not found for drift apply : " + resourceId));
		subprogram.updateTreeRootPath(newPath.toString());
		log.info(
				"[reconciliation] Subprogram treeRootPath 갱신. subprogramId={}, kind={}, newPath={}",
				resourceId, subprogram.getKind(), newPath
		);
	}

	@Override
	public Optional<String> recomputeManifestHash(Markable markable) {
		try {
			return Optional.of(bundleManifestService.compute(markable.getResourcePath()).manifestHash());
		} catch (RuntimeException e) {
			log.warn(
					"[reconciliation] Subprogram manifestHash 재계산 실패. id={}, path={}, msg={}",
					markable.getResourceId(), markable.getResourcePath(), e.getMessage()
			);
			return Optional.empty();
		}
	}

	// ---- MK3 — Trash SPI ---------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findTrashed() {
		return subprogramRepository.findByIsDeletedTrueOrderByTrashedAtDesc().stream().<Markable>map(s -> s).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Markable> findTrashedById(Long resourceId) {
		return subprogramRepository.findById(resourceId)
				.filter(com.example.serverprovision.management.subprogram.entity.Subprogram::isDeleted)
				.<Markable>map(s -> s);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findTrashedBefore(java.time.Instant threshold) {
		return subprogramRepository.findByIsDeletedTrueAndTrashedAtBefore(threshold).stream().<Markable>map(s -> s).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findTrashedBetween(java.time.Instant start, java.time.Instant end) {
		return subprogramRepository.findByIsDeletedTrueAndTrashedAtBetween(start, end).stream().<Markable>map(s -> s).toList();
	}

	@Override
	@Transactional
	public void extendTrashTtl(Long resourceId) {
		Subprogram sp = subprogramRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("Subprogram not found for TTL extend: " + resourceId));
		sp.markTrashed(sp.getTrashedPath());
	}

	@Override
	public void restoreFromTrash(Long resourceId) {
		subprogramService.restore(resourceId);
	}

	@Override
	public void purgeFromTrash(Long resourceId) {
		subprogramService.purge(resourceId);
	}

	// ---- MK3-1 — Ghost SPI -------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public boolean isGhost(Long resourceId) {
		return subprogramRepository.findById(resourceId).map(GhostEvaluator::isGhost).orElse(false);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findGhostMarkables() {
		return subprogramRepository.findByIsDeletedTrueAndTrashedPathIsNull().stream()
				.filter(GhostEvaluator::isGhost)
				.<Markable>map(s -> s)
				.toList();
	}

	@Override
	@Transactional
	public void applyGhostClear(Long resourceId) {
		Subprogram sp = subprogramRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("Subprogram not found for ghost clear: " + resourceId));
		if (!GhostEvaluator.isGhost(sp)) {
			throw new GhostClearTargetNotGhostException(supportedType().name() + "#" + resourceId);
		}
		subprogramRepository.delete(sp);
		log.info("[ghost] Subprogram row 정리. subprogramId={}", resourceId);
	}

	@Override
	@Transactional
	public void applyForcedClear(Long resourceId) {
		// MK3-2 (DCM3-2.5) — 사용자 명시 "강제 정리". lifecycle / FS 검증 없이 row hard-delete.
		Subprogram sp = subprogramRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("Subprogram not found for forced clear: " + resourceId));
		subprogramRepository.delete(sp);
		log.info("[forced-clear] Subprogram row 정리. subprogramId={}", resourceId);
	}
}
