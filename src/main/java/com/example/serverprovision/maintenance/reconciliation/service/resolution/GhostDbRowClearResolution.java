package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import org.springframework.stereotype.Component;

/**
 * GHOST_DB_ROW 해결 — FS 에 자원도 trash 도 없는 dead row 를 hard-delete 한다.
 * ghost 재검증은 {@code applyGhostClear} 구현(도메인 scanner)이 수행 — 비 ghost 대상이면 거절.
 */
@Component
public class GhostDbRowClearResolution implements DriftResolution {

	@Override
	public DriftKind supportedKind() {
		return DriftKind.GHOST_DB_ROW;
	}

	@Override
	public void resolve(Drift drift, MarkableScanner scanner) {
		scanner.applyGhostClear(drift.getResourceId());
	}
}
