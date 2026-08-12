package com.example.serverprovision.maintenance.reconciliation.enums;

import lombok.Getter;

/**
 * MK4-4-2 — 드리프트 이력에 놓이는 사건의 종류.
 *
 * <p>넷이 한 시간축에 섞인다. 점검의 <b>관측</b>, 사람이나 시스템의 <b>처리</b>, 앞선 드리프트가
 * 닫히며 <b>이어진</b> 자리, 이 드리프트가 닫히며 <b>이어 준</b> 자리. 따로 늘어놓으면 읽는
 * 사람이 시간을 머릿속에서 다시 맞춰야 한다.</p>
 *
 * <p>그래프의 표식은 이 종류가 아니라 <b>두 축의 조합</b>에서 온다 — 이 드리프트 자신의 일인가
 * ·  그 대상이 닫혔는가({@code DriftTimelineEntry.nodeClass}). 종류는 링크가 어디로 가는지를
 * 가르는 데 쓴다.</p>
 */
public enum DriftTimelineKind {

	/** 점검이 이 드리프트를 보았다. */
	OBSERVATION,

	/** 사람이나 시스템이 손을 댔다. */
	HANDLING,

	/** 앞선 드리프트가 닫히며 이 드리프트로 이어졌다 — 사슬의 과거 쪽 이음매. */
	SUCCESSION,

	/** 이 드리프트가 닫히며 다른 드리프트로 이어 주었다 — 사슬의 이후 쪽 이음매. */
	SUCCEEDED_BY;

	public boolean isObservation() {
		return this == OBSERVATION;
	}
}
