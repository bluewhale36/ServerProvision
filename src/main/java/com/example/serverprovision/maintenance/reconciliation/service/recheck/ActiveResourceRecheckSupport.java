package com.example.serverprovision.maintenance.reconciliation.service.recheck;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import lombok.RequiredArgsConstructor;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * S6-3-3 — 사용 중 자원 카드(자원 소실·서명 불일치)의 공용 재확인 로직.
 * "제자리에 마커·유효 서명·본체가 전부 돌아왔는가"의 단일 자원 완전 판정 — 전체 점검의
 * 제자리 확인(4a)과 같은 기준을 quick 수준(내용 지문 제외)으로 적용한다.
 */
@RequiredArgsConstructor
public abstract class ActiveResourceRecheckSupport implements DriftRecheck {

	protected final ProvisionMarkerService markerService;

	@Override
	public boolean isResolved(Drift drift, MarkableScanner scanner) {
		Markable resource = scanner.findActiveMarkableById(drift.getResourceId()).orElse(null);
		if (resource == null) {
			// 자원이 삭제·소멸됨 — 이 카드가 가리키던 문제 자체가 의미를 잃음 → 해소로 정리.
			return true;
		}
		Path path = resource.getResourcePath();
		MarkerLayout layout = resource.getMarkerLayout();
		Path markerFile = markerService.resolveMarkerFile(path, layout);
		if (markerFile == null || !Files.exists(markerFile)) {
			return false;
		}
		MarkerContent content;
		try {
			content = markerService.read(path, layout);
		} catch (RuntimeException e) {
			return false;
		}
		if (!markerService.verifySignature(content)) {
			return false;
		}
		boolean bodyExists = layout == MarkerLayout.IN_TREE
				? Files.isDirectory(path)
				: Files.isRegularFile(path);
		return bodyExists;
	}
}
