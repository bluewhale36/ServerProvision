package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;

import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;

import java.time.Instant;

/**
 * 단건 드리프트 응답. {@code Drift} JPA 엔티티를 뷰/REST 에 노출할 때 매핑된다.
 *
 * @param id           DB PK — 단일 admin 환경이라 외부 식별자로 그대로 사용
 * @param resourceType 자원 종류
 * @param resourceId   자원 PK (도메인별)
 * @param displayName  R9-5 — 스캔 시점 자원 표시명 스냅샷. 도입 이전 행은 null (화면이 종전 표기로 fallback)
 * @param kind         드리프트 종류
 * @param oldPath      DB 가 알고 있던 경로 (스캔 시점 기준)
 * @param newPath      재발견된 경로. PATH_DRIFT 일 때만 의미. 그 외엔 null
 * @param firstDetectedAt 이 문제를 처음 본 시각 — MK4-1. 종전 detectedAt("이 회차에 봤다")의 개명
 * @param lastObservedAt  마지막으로 관측된 시각
 * @param observationCount 관측 횟수. 1 이면 이번 점검에서 처음 보인 문제
 * @param status          조치 필요 · 해결됨 · 두고 보기
 * @param snoozeUntil     두고 보기 만료 시각. 조건형이거나 두고 보기가 아니면 null
 * @param snoozeReason    두고 보기 사유
 * @param resolveBlockReason 해결할 수 없는 사유. null 이면 가능하다. {@code Drift.resolveBlockReason()} 이
 *                           그대로 실려 오므로 화면의 버튼 비활성 조건과 서버 가드가 같은 판정을 본다
 * @param snoozeBlockReason 두고 보기를 걸 수 없는 사유. null 이면 가능하다.
 *                          {@code Drift.snoozeBlockReason()} 이 그대로 실려 오므로 화면의 버튼 비활성
 *                          조건과 서버 가드가 같은 판정을 본다 — 두 곳에 조건을 복붙하면 drift 가 생긴다
 * @param detail       자유 텍스트 추가 정보. SIGNATURE_INVALID 등에 변조 정황 메시지가 들어간다
 */
public record DriftResponse(
		Long id,
		ResourceType resourceType,
		Long resourceId,
		String displayName,
		DriftKind kind,
		String oldPath,
		String newPath,
		Instant firstDetectedAt,
		Instant lastObservedAt,
		int observationCount,
		DriftStatus status,
		Instant snoozeUntil,
		String snoozeReason,
		String snoozeBlockReason,
		String resolveBlockReason,
		String detail
) {

	/**
	 * 이번 점검에서 처음 보인 문제인가. 화면이 '최초 발견' 표시를 붙이는 근거다.
	 */
	public boolean isNew() {
		return observationCount <= 1;
	}

}
