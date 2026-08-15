package com.example.serverprovision.maintenance.reconciliation.dto.response;

import java.util.List;

/**
 * MK4-4-2 — 일괄 해결의 결과.
 *
 * <p>몇 건을 집었고 그중 몇 건이 됐는지를 나눠 담는다. 한 건이라도 실패할 수 있는데 "완료" 하나로
 * 답하면 사용자가 목록을 눈으로 세어 확인해야 한다. 실패 사유를 함께 싣는 이유도 같다 — 무엇이 왜
 * 남았는지 모르면 다시 누르는 것 말고 할 수 있는 일이 없다.</p>
 *
 * @param requested 집어간 대상 수
 * @param applied   실제로 해결된 수
 * @param failures  실패한 건의 사유. 건별로 한 줄이며, 성공만 있으면 비어 있다
 */
public record BulkApplyResponse(int requested, int applied, List<String> failures) {

	public int failed() {
		return failures.size();
	}

	/** 화면이 토스트 문구를 고르는 근거. 부분 성공을 성공으로 뭉뚱그리지 않는다. */
	public boolean allApplied() {
		return failures.isEmpty();
	}
}
