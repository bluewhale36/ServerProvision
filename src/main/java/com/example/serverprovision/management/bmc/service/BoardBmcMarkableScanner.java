package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.global.trash.exception.GhostClearTargetNotGhostException;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BMC_FIRMWARE 도메인 어댑터.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardBmcMarkableScanner implements MarkableScanner {

	private final BmcRepository bmcRepository;
	private final com.example.serverprovision.management.bios.service.BundleManifestService bundleManifestService;
	private final BmcLifecycleService bmcLifecycleService;

	@Override
	public ResourceType supportedType() {
		return ResourceType.BMC_FIRMWARE;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findActiveMarkables() {
		return bmcRepository.findAllByIsDeletedFalse().stream()
				.<Markable>map(b -> b)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Markable> findActiveMarkableById(Long resourceId) {
		return bmcRepository.findById(resourceId)
				.filter(b -> !b.isDeleted())
				.map(b -> b);
	}

	@Override
	@Transactional
	public void applyDriftedPath(Long resourceId, Path newPath) {
		BoardBMC bmc = bmcRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("BMC not found for drift apply : " + resourceId));
		bmc.updateTreeRootPath(newPath.toString());
		log.info("[reconciliation] BMC treeRootPath 갱신. bmcId={}, newPath={}", resourceId, newPath);
	}

	@Override
	public Optional<String> recomputeManifestHash(Markable markable) {
		try {
			return Optional.of(bundleManifestService.compute(markable.getResourcePath()).manifestHash());
		} catch (RuntimeException e) {
			log.warn(
					"[reconciliation] BMC manifestHash 재계산 실패. bmcId={}, path={}, msg={}",
					markable.getResourceId(), markable.getResourcePath(), e.getMessage()
			);
			return Optional.empty();
		}
	}

	// ---- MK3 — Trash SPI ---------------------------------------------

	@Override
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public List<Markable> findTrashed() {
		return bmcRepository.findByIsDeletedTrueOrderByTrashedAtDesc().stream().<Markable>map(b -> b).toList();
	}

	@Override
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public Optional<Markable> findTrashedById(Long resourceId) {
		return bmcRepository.findById(resourceId)
				.filter(com.example.serverprovision.management.bmc.entity.BoardBMC::isDeleted)
				.<Markable>map(b -> b);
	}

	@Override
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public List<Markable> findTrashedBefore(java.time.Instant threshold) {
		return bmcRepository.findByIsDeletedTrueAndTrashedAtBefore(threshold).stream().<Markable>map(b -> b).toList();
	}

	@Override
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public List<Markable> findTrashedBetween(java.time.Instant start, java.time.Instant end) {
		return bmcRepository.findByIsDeletedTrueAndTrashedAtBetween(start, end).stream().<Markable>map(b -> b).toList();
	}

	@Override
	public boolean supportsTrashTtlExtension() {
		return true;   // HF4-1 — 파일 자원은 보존기간 연장 지원 (UI 버튼 활성 + 서버 가드 통과의 공유 SSOT)
	}

	@Override
	@org.springframework.transaction.annotation.Transactional
	public void extendTrashTtl(Long resourceId, int days) {
		com.example.serverprovision.management.bmc.entity.BoardBMC bmc = bmcRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("BMC not found for TTL extend: " + resourceId));
		// HF4-1 — 재기준(markTrashed 재호출) 대신 가산 : trashed_at 불변, 만료일만 +days.
		bmc.extendTrashTtl(days);
	}

	@Override
	public void restoreFromTrash(Long resourceId) {
		// R5-3 — 1-arg 위임. 부모 boardId 는 lifecycle service 가 entity 관계로 내부 resolve.
		bmcLifecycleService.restore(resourceId);
	}

	@Override
	public void purgeFromTrash(Long resourceId) {
		bmcLifecycleService.purge(resourceId);
	}

	// ---- MK3-1 — Ghost SPI -------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public boolean isGhost(Long resourceId) {
		return bmcRepository.findById(resourceId).map(GhostEvaluator::isGhost).orElse(false);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Markable> findGhostMarkables() {
		return bmcRepository.findByIsDeletedTrueAndTrashedPathIsNull().stream()
				.filter(GhostEvaluator::isGhost)
				.<Markable>map(b -> b)
				.toList();
	}

	@Override
	@Transactional
	public void applyGhostClear(Long resourceId) {
		BoardBMC bmc = bmcRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("BMC not found for ghost clear: " + resourceId));
		if (!GhostEvaluator.isGhost(bmc)) {
			throw new GhostClearTargetNotGhostException(supportedType().name() + "#" + resourceId);
		}
		bmcRepository.delete(bmc);
		log.info("[ghost] BMC row 정리. bmcId={}", resourceId);
	}

	@Override
	@Transactional
	public void applyForcedClear(Long resourceId) {
		// MK3-2 (DCM3-2.5) — 사용자 명시 "강제 정리". lifecycle / FS 검증 없이 row hard-delete.
		BoardBMC bmc = bmcRepository.findById(resourceId)
				.orElseThrow(() -> new IllegalStateException("BMC not found for forced clear: " + resourceId));
		bmcRepository.delete(bmc);
		log.info("[forced-clear] BMC row 정리. bmcId={}", resourceId);
	}
}
