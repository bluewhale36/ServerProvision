package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * SOFTDEL_MARKER_STRAY(미아 마커) 해결 — 삭제 자원의 마커가 본체 없이 발견된 자리에서 그 마커를 지운다.
 * 자원 실물 · 기록은 건드리지 않는 무해한 청소라 AUTO 등급이며, 삭제는 멱등(이미 없으면 성공 취급) —
 * {@link StaleTrashMarkerCleanupResolution} 과 같은 원칙의 활성 트리 판이다.
 *
 * <p>실행 직전 재확인 — 발견 위치에 본체가 나타났다면(운영자가 파일을 되돌려 둠) 더 이상 미아 마커
 * 상태가 아니고, 마커를 지우면 실물에서 신원 증명을 떼는 셈이 되므로 재점검을 안내한다(stale 화면 안전망).
 * 자원 row 가 이미 복원 · 영구삭제됐다면 이 보고 자체가 낡은 것이므로 동일하게 거절한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrayMarkerCleanupResolution implements DriftResolution {

	private final ProvisionMarkerService markerService;

	@Override
	public DriftKind supportedKind() {
		return DriftKind.SOFTDEL_MARKER_STRAY;
	}

	@Override
	public Optional<Path> resolve(Drift drift, MarkableScanner scanner) {
		Markable resource = scanner.findTrashedById(drift.getResourceId())
				.orElseThrow(DriftResolutionNotAllowedException::staleState);
		Path foundAt = Path.of(drift.getNewPath());
		if (resource.getMarkerLayout().resourceBodyExists(foundAt)) {
			throw DriftResolutionNotAllowedException.staleState();
		}

		Path strayMarker = markerService.resolveMarkerFile(foundAt, resource.getMarkerLayout());
		try {
			Files.deleteIfExists(strayMarker);
		} catch (IOException e) {
			throw new IllegalStateException("미아 마커 정리 실패 — 디스크 상태 점검 필요 : " + strayMarker, e);
		}
		log.info("[reconciliation] 미아 마커 정리 완료. {}#{} marker={}",
				drift.getResourceType(), drift.getResourceId(), strayMarker);
		// 마커는 옮기지 않고 지운다 — 필요 시 재발급으로 되살린다.
		return Optional.empty();
	}
}
