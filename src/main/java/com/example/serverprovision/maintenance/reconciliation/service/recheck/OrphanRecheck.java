package com.example.serverprovision.maintenance.reconciliation.service.recheck;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 미등록 마커 카드의 [다시 점검] — "그 자리에 마커가 아직 있고 여전히 주인이 없는가"를 재확인한다.
 * 활성 자원 공용 로직을 쓰면 미등록 자원(DB 조회 불가)에서 항상 '해소' 오판이 나므로 전용 전략
 * (S6-3 plan 적대적 검증 반영 — 거짓 해소 방지).
 */
@Component
@RequiredArgsConstructor
public class OrphanRecheck implements DriftRecheck {

	private final ProvisionMarkerService markerService;

	@Override
	public DriftKind supportedKind() {
		return DriftKind.ORPHAN;
	}

	@Override
	public boolean isResolved(Drift drift, MarkableScanner scanner) {
		// 그 사이 DB 에 주인이 생겼으면(재등록) 해소.
		if (scanner.findActiveMarkableById(drift.getResourceId()).isPresent()
				|| scanner.findTrashedById(drift.getResourceId()).isPresent()) {
			return true;
		}
		MarkerContent marker;
		try {
			marker = markerService.read(
					Path.of(drift.getOldPath()), drift.getResourceType().getDefaultLayout());
		} catch (RuntimeException e) {
			return true; // 마커가 정리·이동됨 — 이 카드의 문제는 소멸 (새 상태는 다음 점검 몫)
		}
		if (!drift.getResourceType().name().equals(marker.resourceType())
				|| !drift.getResourceId().equals(marker.resourceId())) {
			return true; // 신분이 바뀜(교체·재발급) — 이 카드가 가리키던 orphan 은 소멸
		}
		return false; // 여전히 주인 없는 마커가 그 자리에 있음
	}
}
