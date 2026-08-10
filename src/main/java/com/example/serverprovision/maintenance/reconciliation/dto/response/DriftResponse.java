package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;

import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.vo.DriftPriority;
import com.example.serverprovision.provisioning.usage.ResourceUsageLevel;

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
 * @param usage        MK4-2 — 이 자원이 지금 쓰이는 깊이. 처리 순서의 보정 축이며 화면의 배지 근거다.
 *                     위험도는 {@code kind.severity} 로 종류에서 파생되므로 따로 싣지 않는다.
 *                     <b>목록을 조립할 때만 채워지고 그 밖에서는 {@code null}</b> 이다 — 사용 여부는
 *                     드리프트와 무관하게 변하는 현재값이라, 계산하지 않은 자리에 기본값을 넣으면
 *                     "쓰이지 않는다" 는 사실과 "확인하지 않았다" 를 구분할 수 없게 된다
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
		String detail,
		ResourceUsageLevel usage
) {

	/**
	 * 이번 점검에서 처음 보인 문제인가. 화면이 '최초 발견' 표시를 붙이는 근거다.
	 */
	public boolean isNew() {
		return observationCount <= 1;
	}

	/**
	 * MK4-2 — 이 문제의 처리 순서. 위험도는 종류에서 파생되고 사용 깊이는 조회 시점에 실려 온다.
	 * 사용 깊이를 계산하지 않은 응답은 미사용으로 간주해 정렬만 가능하게 한다.
	 */
	public DriftPriority priority() {
		return new DriftPriority(
				kind.getSeverity(),
				usage != null ? usage : ResourceUsageLevel.NONE,
				firstDetectedAt);
	}

	/** 화면이 사용 중 배지를 띄울지 판단한다 — 계산하지 않았거나 쓰이지 않으면 띄우지 않는다. */
	public boolean hasUsage() {
		return usage != null && usage != ResourceUsageLevel.NONE;
	}
}
