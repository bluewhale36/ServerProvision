package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SIGNATURE_INVALID(마커 서명 불일치) 해결 — 이 자원의 마커만 현재 키로 재서명한다.
 * 사용자 확인(MANUAL) 전용 — 확인 창에서 "변조가 아니라 손상·이식"임을 사용자가 답한 뒤 실행된다.
 *
 * <p>전체 [마커 서명 재발급](키 회전용 관리 도구)의 단일 자원판 — 같은 안전 설계를 계승한다:
 * <b>내용 지문(manifestHash)은 유지</b>하므로 실제 변조된 파일이라면 다음 정밀 점검이 여전히 잡는다.
 * 단 발각 시점은 다음 정밀 점검(최대 하루)까지 늦을 수 있음을 확인 창 문구가 명시한다.</p>
 *
 * <p>마커가 파싱 불가 수준으로 손상된 경우는 재서명이 아니라 재구성(자원 정보 합성)이 필요한
 * 다른 등급의 작업이라 거절하고 안내한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignatureReissueResolution implements DriftResolution {

	private final ProvisionMarkerService markerService;

	@Override
	public DriftKind supportedKind() {
		return DriftKind.SIGNATURE_INVALID;
	}

	@Override
	public void resolve(Drift drift, MarkableScanner scanner) {
		Markable resource = scanner.findActiveMarkableById(drift.getResourceId())
				.orElseThrow(DriftResolutionNotAllowedException::staleState); // 그 사이 삭제/소멸됨

		MarkerContent existing;
		try {
			existing = markerService.read(resource.getResourcePath(), resource.getMarkerLayout());
		} catch (RuntimeException e) {
			throw DriftResolutionNotAllowedException.markerUnreadable();
		}

		MarkerContent unsigned = existing.withoutSignature();
		String newSignature = markerService.computeSignature(unsigned);
		markerService.write(resource.getResourcePath(), resource.getMarkerLayout(),
				unsigned.withSignature(newSignature));
		// 내용 지문은 기존 값 그대로 — 변조 가능성을 굳히지 않는다 (전체 재발급과 동일).
		resource.reissueMarker(existing.manifestHash(), newSignature);
		log.warn("[AUDIT] 단일 마커 재서명 — {}#{} (drift {} 사용자 확인 경유)",
				drift.getResourceType(), drift.getResourceId(), drift.getId());
	}
}
