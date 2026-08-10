package com.example.serverprovision.maintenance.reconciliation.vo;

import java.time.Instant;

/**
 * MK4-2 — 이번 점검이 무엇까지 보았는가.
 *
 * <p>일반 점검은 마커와 위치만 보고 파일 내용은 정밀 점검에서만 다시 계산한다. 그래서 아무도
 * 아무것도 고치지 않았는데 건수가 줄어드는 일이 생긴다 — 내용에 관한 문제를 <b>해결한 것이 아니라
 * 보지 않은 것</b>인데 화면은 둘을 구분해 주지 않았다.</p>
 *
 * <p>이 값이 화면에 세 가지를 만든다 — 목록 위의 안내, 내용에 관한 자리의 "확인 안 됨" 표시,
 * 그리고 항상 함께 보이는 마지막 정밀 점검 시각이다.</p>
 *
 * @param contentChecked  이번 점검이 파일 내용을 확인했는가(정밀 점검이었는가)
 * @param lastDeepScanAt  마지막으로 내용을 확인한 시각. 정밀 점검 이력이 없으면 {@code null}
 */
public record ScanCoverage(
		boolean contentChecked,
		Instant lastDeepScanAt
) {

	/** 아직 어떤 점검도 없는 화면(빈 상태)의 표현. */
	public static ScanCoverage none() {
		return new ScanCoverage(false, null);
	}

	/** 정밀 점검을 한 번도 한 적이 없는가 — 화면이 시각 자리를 비워야 하는지 판단한다. */
	public boolean neverDeepScanned() {
		return lastDeepScanAt == null;
	}
}
