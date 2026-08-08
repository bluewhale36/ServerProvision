package com.example.serverprovision.maintenance.reconciliation.enums;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

/**
 * MK4-1 — '지금은 두고 보기' 의 기간 또는 조건.
 *
 * <p>{@code int days} 로 받으면 "다음 정밀 점검까지" 같은 조건형을 표현할 수 없고, 0 이나 음수를
 * 막는 검증이 호출부마다 흩어진다. 각 상수가 만료 시각을 스스로 계산하게 두면 호출부는 고르기만
 * 하면 된다.</p>
 *
 * <p>조건형({@link #UNTIL_NEXT_DEEP_SCAN})은 만료 시각이 없다 — 시각이 아니라 사건으로 풀리기
 * 때문이다. 그 경우 문제의 {@code snoozeUntil} 은 비어 있고, 정밀 점검이 그 문제를 관측하는
 * 순간 다시 열린다.</p>
 */
@Getter
public enum SnoozeWindow {

	DAYS_7("7일 동안", Duration.ofDays(7)),
	DAYS_30("30일 동안", Duration.ofDays(30)),
	UNTIL_NEXT_DEEP_SCAN("다음 정밀 점검까지", null);

	private final String label;
	private final Duration duration;

	SnoozeWindow(String label, Duration duration) {
		this.label = label;
		this.duration = duration;
	}

	/**
	 * 만료 시각. 조건형은 시각으로 풀리지 않으므로 null 을 돌려준다.
	 */
	public Instant expiresAt(Instant now) {
		return duration == null ? null : now.plus(duration);
	}

	/**
	 * 정밀 점검이 관측했을 때 풀려야 하는 조건형인가.
	 */
	public boolean releasedByDeepScan() {
		return this == UNTIL_NEXT_DEEP_SCAN;
	}
}
