package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;

import java.nio.file.Path;
import java.util.Optional;

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
	 *
	 * <p>MK4-1 — 파일을 옮긴 전략은 <b>옮겨 둔 위치</b>를 돌려준다. 처리 이력이 되돌리기의 재료로
	 * 그 값을 보관하는데, 도착지를 아는 것은 전략뿐이기 때문이다 — 격리 구역이나 휴지통 경로는
	 * 전략이 실행 중에 계산하므로 {@code Drift} 에는 남아 있지 않다. 종전에는 호출자가 무조건
	 * {@code drift.newPath} 를 적어, 경로 이동됨을 뺀 나머지 종류에서 "되돌릴 수 있다" 고 기록해 놓고
	 * 정작 어디로 갔는지는 비워 두는 어긋남이 있었다(MK4-1 CP5 실측).</p>
	 *
	 * <p>옮긴 것이 없으면 비어 있다. 그 경우 호출자가 {@code drift.newPath} 를 그 자리에 쓴다 —
	 * 경로 이동됨처럼 도착지가 이미 drift 에 적혀 있는 종류가 그렇다. 종류별 분기는 어디에도 생기지
	 * 않는다: 각 전략이 자기 답을 들고 있다.</p>
	 *
	 * @return 이 처리가 파일을 옮겼다면 그 도착 위치, 옮긴 것이 없으면 {@code Optional.empty()}
	 */
	Optional<Path> resolve(Drift drift, MarkableScanner scanner);
}
