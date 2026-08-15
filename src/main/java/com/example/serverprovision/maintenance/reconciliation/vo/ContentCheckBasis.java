package com.example.serverprovision.maintenance.reconciliation.vo;

import java.time.Instant;

/**
 * MK4-4-3 — <b>이 드리프트의 내용 판정은 언제 것인가.</b>
 *
 * <p>{@link ScanCoverage} 와 묻는 것이 다르다. 그쪽은 <b>가장 최근 점검 한 건</b>의 성질이라
 * 매 점검마다 바뀌는 전역값이고, 이쪽은 <b>드리프트 하나</b>의 사실이라 어느 화면에서 어느 경로로
 * 들어오든 같다.</p>
 *
 * <p>둘을 구분하지 않아 드리프트 상세가 모순되어 보였다. 그 화면에는 회차라는 문맥이 없는데
 * "이번 점검은 파일 내용을 보지 않았습니다" 라고 적혀 있었고, 정밀 점검 회차를 타고 들어가도
 * 같은 문장이 나왔다 — 최근 점검을 읽고 있었기 때문이다.</p>
 *
 * <p>말해야 할 사실은 따로 있다. MK4-1 이 드리프트를 회차마다 새로 만드는 행이 아니라 <b>지속
 * 개체</b>로 바꾼 뒤로, 정밀 점검이 잡은 내용 드리프트는 이후 일반 점검을 지나며 <b>재확인되지도
 * 부정되지도 않은 채</b> 살아 있다. 그러니 "확인 안 됨" 이 아니라 <b>언제 확인한 것인지</b>를
 * 말해야 한다 — 모르는 것이 아니라 그때 안 것이다.</p>
 *
 * @param lastDeepObservedAt 이 드리프트를 마지막으로 정밀 점검이 관측한 시각.
 *                           정밀 관측 기록이 없으면 {@code null}
 * @param generalScansSince  그 이후 지나간 일반 점검 횟수. 내용을 보지 않고 지나간 횟수다
 */
public record ContentCheckBasis(
		Instant lastDeepObservedAt,
		long generalScansSince
) {

	/** 내용 판정의 근거가 아직 없는 상태 — 정밀 점검이 이 드리프트를 본 적이 없다. */
	public static ContentCheckBasis unknown() {
		return new ContentCheckBasis(null, 0);
	}

	/** 근거로 삼을 정밀 관측이 있는가. 없으면 화면이 시각 자리를 비워야 한다. */
	public boolean hasBasis() {
		return lastDeepObservedAt != null;
	}

	/**
	 * 그 뒤로 내용을 보지 않고 지나간 점검이 있는가.
	 *
	 * <p>0 이면 판정이 방금 확인한 것이라 덧붙일 말이 없다. 이 구분이 없으면 정밀 점검 직후에도
	 * "이후 0 회는 확인하지 않았습니다" 라는 빈 문장이 남는다.</p>
	 */
	public boolean hasUncheckedScansSince() {
		return generalScansSince > 0;
	}
}
