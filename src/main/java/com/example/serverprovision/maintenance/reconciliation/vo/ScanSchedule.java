package com.example.serverprovision.maintenance.reconciliation.vo;

import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * MK4-3-2 — 점검 일정의 현재 상태. <b>지금이 점검할 때인가</b> 하나에 답한다.
 *
 * <p>이 판정을 스케줄러 밖으로 꺼낸 이유는 검증에 있다. 값이 순수 함수로 계산되므로 스케줄러를 구동하지
 * 않고 진리표로 덮을 수 있다 — Spring 스케줄러의 발동 시각을 조작해야만 확인되는 구조에서는 회귀를
 * 막을 방법이 없다.</p>
 *
 * <p>기준 시각의 비대칭에 주의한다. 일반 점검의 "마지막" 은 <b>정밀을 포함한 마지막 점검</b>이고,
 * 정밀 점검의 "마지막" 은 <b>마지막 정밀 점검</b>이다. 정밀이 일반을 덮으므로 정밀 점검은 일반의 시계도
 * 되돌리지만 반대는 아니기 때문이다. 수동 점검도 기준에 든다 — 주기의 뜻이 "최소 이 간격마다 한 번은
 * 본다" 이므로 방금 사람이 돌린 점검을 못 본 척할 이유가 없다.</p>
 */
public record ScanSchedule(DepthState quick, DepthState deep) {

	/**
	 * 한 깊이의 상태.
	 *
	 * @param interval       설정된 주기
	 * @param lastScanAt     만기 판정의 기준 시각. 기록이 없으면 {@code null}
	 * @param lastDuration   같은 깊이의 마지막 점검이 걸린 시간. 기록이 없으면 {@code null}.
	 *                       주기가 이보다 짧으면 화면이 경고한다 — 근거가 추측이 아니라
	 *                       그 설치 환경의 실측값이라는 점이 중요하다
	 */
	public record DepthState(ScanInterval interval, Instant lastScanAt, Duration lastDuration) {

		public boolean isDue(Instant now) {
			return interval.isDue(lastScanAt, now);
		}

		public Optional<Instant> nextDueAt() {
			return interval.nextDueAt(lastScanAt);
		}

		/** 설정한 주기가 지난 실측 소요 시간보다 짧은가. 막지는 않고 알리기만 한다. */
		public boolean intervalShorterThanLastRun() {
			return interval.shorterThan(lastDuration);
		}
	}

	/**
	 * 지금 무엇을 해야 하는가. 아무것도 할 게 없으면 비어 있다.
	 *
	 * <p>정밀이 먼저다. 둘 다 밀렸을 때 정밀 하나만 돌려도 일반의 요구가 함께 채워지기 때문이며
	 * ({@link ScanDepth#covers}), 이 규칙이 종전 구조에서 두 스케줄이 같은 순간에 겹쳐 정밀이 버려지던
	 * 문제를 없앤다 — 한 번의 심박이 하나의 결정만 내리므로 경합할 자리가 없다.</p>
	 */
	public Optional<ScanDepth> dueDepth(Instant now) {
		if (deep.isDue(now)) return Optional.of(ScanDepth.DEEP);
		if (quick.isDue(now)) return Optional.of(ScanDepth.QUICK);
		return Optional.empty();
	}

	public DepthState of(ScanDepth depth) {
		return depth.isDeep() ? deep : quick;
	}
}
