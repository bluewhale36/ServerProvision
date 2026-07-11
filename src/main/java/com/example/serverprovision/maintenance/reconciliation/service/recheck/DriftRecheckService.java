package com.example.serverprovision.maintenance.reconciliation.service.recheck;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S6-3-3 — [다시 점검] 진입점. 확인 축은 해결([적용])과 별도 서비스로 분리 —
 * 상태를 바꾸지 않는 판정이라 전역 해결 옵션(resolution-enabled)의 지배도 받지 않는다.
 * 해소 시 보고서에서 카드만 제거하고, 잔존·변화 시 카드는 불변(재분류는 전체 점검의 몫).
 */
@Slf4j
@Service
public class DriftRecheckService {

	private final Map<ResourceType, MarkableScanner> scanners;
	private final Map<DriftKind, DriftRecheck> rechecks;
	private final DriftRepository driftRepository;

	public DriftRecheckService(
			List<MarkableScanner> scanners,
			List<DriftRecheck> rechecks,
			DriftRepository driftRepository
	) {
		this.scanners = scanners.stream()
				.collect(Collectors.toUnmodifiableMap(MarkableScanner::supportedType, s -> s));
		this.rechecks = rechecks.stream()
				.collect(Collectors.toUnmodifiableMap(DriftRecheck::supportedKind, r -> r));
		this.driftRepository = driftRepository;
	}

	/**
	 * @return true = 해소 확인되어 카드 제거됨. false = 여전히 문제(카드 유지).
	 */
	@Transactional
	public boolean recheck(Long driftId) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		DriftRecheck recheck = rechecks.get(drift.getKind());
		if (recheck == null || !drift.getKind().isRecheckable()) {
			// UI 는 recheckable 종류에만 버튼을 그림 — direct POST 안전망.
			throw DriftResolutionNotAllowedException.notApplicable(drift.getKind());
		}
		MarkableScanner scanner = scanners.get(drift.getResourceType());
		if (scanner == null) {
			throw new IllegalStateException("지원하지 않는 자원 종류 : " + drift.getResourceType());
		}
		boolean resolved = recheck.isResolved(drift, scanner);
		if (resolved) {
			drift.getReport().removeDrift(drift);
			log.info("[recheck] 해소 확인 — driftId={}, kind={} : 카드 제거", driftId, drift.getKind());
		}
		return resolved;
	}
}
