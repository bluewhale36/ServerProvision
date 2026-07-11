package com.example.serverprovision.maintenance.reconciliation.service.recheck;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;

/**
 * S6-3-3 — [다시 점검] 확인 전략. 해결({@code DriftResolution})과 동형이지만 <b>상태를 바꾸지 않는다</b> —
 * "문제가 아직 있는가"만 답하고, 해소 판정 시 호출자가 보고서에서 카드를 제거한다.
 * 종류마다 확인해야 할 것이 달라(제자리 복원 여부 / 주인 생김 여부) kind 별 전략으로 분리.
 */
public interface DriftRecheck {

	DriftKind supportedKind();

	/**
	 * @return true = 문제가 해소됨(카드 제거 대상). false = 여전히(또는 다른 형태로) 문제 —
	 *         카드는 불변 유지, 재분류는 다음 전체 점검의 몫.
	 */
	boolean isResolved(Drift drift, MarkableScanner scanner);
}
