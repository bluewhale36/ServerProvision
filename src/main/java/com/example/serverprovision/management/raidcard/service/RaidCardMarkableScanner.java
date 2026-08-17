package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * RaidCard 도메인 어댑터 — 메타 자원이라 휴지통 노출용 lifecycle 메타만 노출 (MA7 D1).
 *
 * <p>휴지통 화면은 이 SPI 를 통해서만 자원을 보므로, 파일이 없어도 이 어댑터가 없으면 삭제된
 * 카드가 휴지통에 나타나지 않는다({@code BoardModelMarkableScanner} 선례). 조회(inventory)와
 * 재조정(drift)은 파일 실체가 없어 각각 빈 리스트 · no-op 이고, 자식이 없어
 * {@code findDeletedChildLabels} 는 default(빈 리스트)를 그대로 쓴다.</p>
 */
@Service
@RequiredArgsConstructor
public class RaidCardMarkableScanner implements MarkableScanner {

	private final RaidCardRepository raidCardRepository;
	private final RaidCardLifecycleService raidCardLifecycleService;

	@Override
	public ResourceType supportedType() {
		return ResourceType.RAID_CARD;
	}

	@Override
	public List<Markable> findActiveMarkables() {
		return Collections.emptyList();
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
		return raidCardRepository.findAllByIsDeletedTrue().stream()
				.<Markable>map(c -> c)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Markable> findTrashedById(Long resourceId) {
		return raidCardRepository.findByIdAndIsDeletedTrue(resourceId).<Markable>map(c -> c);
	}

	@Override
	public void restoreFromTrash(Long resourceId, boolean cascade) {
		raidCardLifecycleService.restore(resourceId, cascade);
	}

	@Override
	public void restoreFromTrash(Long resourceId) {
		raidCardLifecycleService.restore(resourceId, false);
	}

	@Override
	public void purgeFromTrash(Long resourceId) {
		raidCardLifecycleService.purge(resourceId);
	}
}
