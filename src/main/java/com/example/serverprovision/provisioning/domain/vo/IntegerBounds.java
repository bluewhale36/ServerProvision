package com.example.serverprovision.provisioning.domain.vo;

/**
 * Integer 속성의 값 제약 ({@code LowerBound} / {@code UpperBound} / {@code ScalarIncrement}).
 * {@code scalarIncrement} 이 0 이면 자유 입력(증분 제약 없음)으로 해석한다 — %0 연산을 절대 수행하지 않는다.
 */
public record IntegerBounds(long lower, long upper, long scalarIncrement) {

	/** HTML {@code <input step>} 용. 0/음수 증분은 1 로 정규화. */
	public long htmlStep() {
		return scalarIncrement <= 0 ? 1 : scalarIncrement;
	}

	/** 범위 + 증분 정렬 검증. 증분 anchor 는 0 이 아니라 {@code lower}. */
	public boolean isValid(long n) {
		if (n < lower || n > upper) {
			return false;
		}
		if (scalarIncrement <= 0) {
			return true;
		}
		return (n - lower) % scalarIncrement == 0;
	}
}
