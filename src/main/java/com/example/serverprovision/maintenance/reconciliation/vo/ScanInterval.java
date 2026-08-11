package com.example.serverprovision.maintenance.reconciliation.vo;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * MK4-3-2 — 점검 주기.
 *
 * <p>주기는 하한 · 상한이 있고 사람이 읽는 표기가 붙는 값이다. {@code long ms} 로 들고 다니면 그 셋이
 * 코드 곳곳으로 흩어지고, 밀리초 원문은 화면에서 자릿수 오타를 부른다({@code 3600000} 과
 * {@code 360000} 은 눈으로 구별되지 않는다). 그래서 값으로 타입화하고 단위는 <b>분</b>으로 둔다 —
 * 심박이 1 분마다 판정하므로 그보다 잘게 설정할 수 있게 하면 설정한 값과 실제 동작이 어긋난다.</p>
 *
 * <p>하한을 1 분으로 둔 이유는 CP1 Q1 에서 확정했다. 주기가 점검 소요 시간보다 짧으면 겹친 회차가
 * 건너뛰어질 뿐 무엇도 깨지지 않으므로(동시 실행 가드가 이미 막는다) 거절할 근거가 되지 못한다.
 * 짧은 주기는 위험한 것이 아니라 현명하지 않은 것이고, 그 사실은 화면이 실측값으로 알린다.</p>
 */
public record ScanInterval(Duration value) {

	public static final int MIN_MINUTES = 1;

	/** 30 일. 이보다 길면 주기라기보다 사실상 하지 않는 것이라 상한을 둔다. */
	public static final int MAX_MINUTES = 43_200;

	public ScanInterval {
		if (value == null) {
			throw new IllegalArgumentException("점검 주기는 비어 있을 수 없다");
		}
		long minutes = value.toMinutes();
		if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
			throw new IllegalArgumentException(
					"점검 주기는 %d~%d 분이어야 한다 : %d".formatted(MIN_MINUTES, MAX_MINUTES, minutes));
		}
	}

	public static ScanInterval ofMinutes(long minutes) {
		return new ScanInterval(Duration.ofMinutes(minutes));
	}

	public long toMinutes() {
		return value.toMinutes();
	}

	/**
	 * 마지막 점검으로부터 이 주기가 지났는가.
	 *
	 * <p>기준 시각이 없으면 항상 참이다 — 한 번도 안 봤으면 지금 봐야 한다. 정확히 주기만큼 지난
	 * 순간도 만기로 본다(경계 포함).</p>
	 */
	public boolean isDue(Instant lastScanAt, Instant now) {
		return lastScanAt == null || !now.isBefore(lastScanAt.plus(value));
	}

	/**
	 * 다음 점검 예정 시각. 기준 시각이 없으면 비어 있다 — 예정이 아니라 지금 밀려 있다는 뜻이다.
	 *
	 * <p>이 값이 계산 가능하다는 것이 이 설계의 핵심이다. 스케줄러 내부에 발동 시각이 숨는 구조에서는
	 * 화면이 "언제 다음에 볼지" 를 말할 수 없다.</p>
	 */
	public Optional<Instant> nextDueAt(Instant lastScanAt) {
		return Optional.ofNullable(lastScanAt).map(last -> last.plus(value));
	}

	/** 이 주기가 그만큼 걸리는 점검을 담을 수 있는가. 담지 못하면 겹친 회차가 건너뛰어진다. */
	public boolean shorterThan(Duration scanDuration) {
		return scanDuration != null && !scanDuration.isZero() && value.compareTo(scanDuration) < 0;
	}

	/** 사람이 읽는 표기. 화면이 입력값 옆에 함께 보여 준다. */
	public String display() {
		long minutes = toMinutes();
		if (minutes % (24 * 60) == 0) return "%d분 (%d일)".formatted(minutes, minutes / (24 * 60));
		if (minutes % 60 == 0) return "%d분 (%d시간)".formatted(minutes, minutes / 60);
		return "%d분".formatted(minutes);
	}
}
