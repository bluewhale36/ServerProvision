package com.example.serverprovision.maintenance.reconciliation.service.recheck;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import org.springframework.stereotype.Component;

/**
 * 자원 소실 카드의 [다시 점검] — 안내대로 파일을 원래 자리·이름으로 복원한 뒤 즉시 확인하는 용도.
 */
@Component
public class MissingRecheck extends ActiveResourceRecheckSupport {

	public MissingRecheck(ProvisionMarkerService markerService) {
		super(markerService);
	}

	@Override
	public DriftKind supportedKind() {
		return DriftKind.MISSING;
	}
}
