package com.example.serverprovision.maintenance.reconciliation.dto.response;

import java.util.List;

/**
 * MK4-4-2 — 드리프트 하나의 이력 전체. 관측 · 처리 · 이어짐이 한 시간축에 놓인다.
 *
 * <p>관측은 점검마다 쌓여 오래 남은 드리프트는 수십 · 수백 회에 이르므로 최근 것만 보인다. 다만
 * <b>몇 건을 감췄는지는 밝힌다</b> — 잘라 놓고 말하지 않으면 보이는 것이 전부인 줄 알게 된다.</p>
 *
 * <p>계보는 감추지 않는다. 사슬의 시작이 어디인지는 건수와 무관하게 알아야 하는 사실이고,
 * 관측이 많다는 이유로 그것이 잘려 나가면 "이 드리프트는 여기서 시작했다" 는 거짓이 남는다.</p>
 *
 * @param entries 최근 것부터. 감춘 구간 뒤에 계보가 이어진다
 * @param hidden  자리 관계로 보이지 않는 관측 · 처리의 건수. 0 이면 전부 보이고 있다
 */
public record DriftTimelineResponse(List<DriftTimelineEntry> entries, int hidden) {

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/** 화면 머리의 건수. 감춘 것까지 포함한 이 드리프트의 전체 사건 수다. */
	public int total() {
		return entries.size() + hidden;
	}
}
