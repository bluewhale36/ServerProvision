package com.example.serverprovision.global.marker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IntegrityStatus} 의 라벨 단일 소스 검증.
 *
 * <p>R5-2 — 4 verification launcher 에 복제됐던 statusMessage switch 를 enum 생성자 필드로 흡수했으므로,
 * 각 상수가 추출 전과 동일한 사용자 안내 문구를 반환하는지 확인한다(동작 불변 refactor 의 라벨 정합성).</p>
 */
class IntegrityStatusTest {

	@DisplayName("getDisplayMessage: 5 상수 각각 정확한 라벨을 반환한다")
	@ParameterizedTest(name = "{0} → \"{1}\"")
	@CsvSource({
			"ORIGINAL,원본 유지",
			"TAMPERED,변조 감지 (해시 불일치)",
			"SIGNATURE_INVALID,서명 무효",
			"MARKER_MISSING,마커 파일 없음",
			"NOT_VERIFIED,미검증"
	})
	void getDisplayMessage_returnsExactLabel(IntegrityStatus status, String expectedLabel) {
		assertThat(status.getDisplayMessage()).isEqualTo(expectedLabel);
	}
}
