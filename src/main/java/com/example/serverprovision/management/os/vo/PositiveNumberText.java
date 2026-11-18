package com.example.serverprovision.management.os.vo;

import java.util.Optional;

/** 버전 문자열 조각을 1 이상의 정수로 읽는다(S23-1). 숫자만으로 된 조각이 아니거나 0 이면 empty. 메이저 버전과 빌드 번호가 같이 쓴다. */
final class PositiveNumberText {

	private PositiveNumberText() {
	}

	static Optional<Integer> parse(String text) {
		if (text == null || text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
			return Optional.empty();
		}
		try {
			int value = Integer.parseInt(text);
			return value > 0 ? Optional.of(value) : Optional.empty();
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
