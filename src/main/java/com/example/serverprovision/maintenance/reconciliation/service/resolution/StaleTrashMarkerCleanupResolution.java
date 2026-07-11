package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.entity.LifecycleEntity;
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

/**
 * TRASH_MARKER_STALE(잔여 마커 정리 필요) 해결 — 휴지통 실물 옆에 남은 마커 찌꺼기를 지운다.
 * 자원·기록은 건드리지 않는 무해한 청소라 AUTO 등급이며, 삭제는 멱등(이미 없으면 성공 취급).
 *
 * <p>실행 직전 재확인 — 자원이 더 이상 휴지통에 없거나(복원/영구삭제됨) 실물이 사라졌으면
 * 이 보고로는 처리할 수 없으므로 재점검을 안내한다(stale 화면 안전망).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleTrashMarkerCleanupResolution implements DriftResolution {

	private final ProvisionMarkerService markerService;

	@Override
	public DriftKind supportedKind() {
		return DriftKind.TRASH_MARKER_STALE;
	}

	@Override
	public void resolve(Drift drift, MarkableScanner scanner) {
		Markable resource = scanner.findTrashedById(drift.getResourceId())
				.orElseThrow(DriftResolutionNotAllowedException::staleState);
		if (!(resource instanceof LifecycleEntity lifecycle) || lifecycle.getTrashedPath() == null
				|| !Files.exists(Path.of(lifecycle.getTrashedPath()))) {
			throw DriftResolutionNotAllowedException.staleState();
		}

		Path staleMarker = markerService.resolveMarkerFile(
				Path.of(lifecycle.getTrashedPath()), resource.getMarkerLayout());
		try {
			Files.deleteIfExists(staleMarker);
		} catch (IOException e) {
			throw new IllegalStateException("잔여 마커 정리 실패 — 디스크 상태 점검 필요 : " + staleMarker, e);
		}
		log.info("[reconciliation] 잔여 마커 정리 완료. {}#{} marker={}",
				drift.getResourceType(), drift.getResourceId(), staleMarker);
	}
}
