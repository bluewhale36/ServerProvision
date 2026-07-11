package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;

/**
 * S6-2-1 — drift 종류별 시스템 해결 전략.
 *
 * <p>{@code PathReconciliationService.apply} 의 kind if-else(과거 GHOST 분기 + "나머지는 전부
 * {@code applyDriftedPath(newPath)}")를 kind 별 bean 으로 치환하는 확장점. 신규 kind 의 해결은
 * 새 구현 bean 1개 추가로 끝나고 서비스 본문 분기는 늘지 않는다(OCP). newPath 가 없는 kind 에서
 * {@code Path.of(null)} NPE 가 잠복하던 문제도 함께 소멸.</p>
 *
 * <p>구현은 해결 동작만 수행한다 — 보고서에서 drift 를 제거하는 후처리는 수동 apply() 전용이고,
 * 스캔 무인 적용은 기록 보존을 위해 drift 를 남긴다(호출자 책임 분리).</p>
 */
public interface DriftResolution {

	/**
	 * 본 전략이 해결하는 drift 종류. 1 bean = 1 kind — Map 조립 시 중복 kind 는 즉시 실패한다.
	 */
	DriftKind supportedKind();

	/**
	 * 해결 실행. scanner 는 해당 drift 의 resourceType 에 매칭되는 도메인 어댑터를 호출자가 조회해 넘긴다
	 * (구현 bean 이 scanner 목록을 자체 보유하지 않게 — 조회 책임은 서비스에 이미 존재).
	 */
	void resolve(Drift drift, MarkableScanner scanner);
}
