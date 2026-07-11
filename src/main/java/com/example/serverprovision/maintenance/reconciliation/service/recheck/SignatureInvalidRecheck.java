package com.example.serverprovision.maintenance.reconciliation.service.recheck;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import org.springframework.stereotype.Component;

/**
 * 서명 불일치 카드의 [다시 점검] — 재서명([적용]) 없이 파일·마커를 손으로 정리한 사용자의 확인 용도.
 */
@Component
public class SignatureInvalidRecheck extends ActiveResourceRecheckSupport {

	public SignatureInvalidRecheck(ProvisionMarkerService markerService) {
		super(markerService);
	}

	@Override
	public DriftKind supportedKind() {
		return DriftKind.SIGNATURE_INVALID;
	}
}
