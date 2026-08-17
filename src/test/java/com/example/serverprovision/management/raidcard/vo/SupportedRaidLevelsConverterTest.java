package com.example.serverprovision.management.raidcard.vo;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MA7 — {@link SupportedRaidLevelsConverter} CSV 왕복 단위 테스트.
 * 미등록 토큰 저장본을 silent 흡수하지 않고 명시 거절하는 계약을 고정한다.
 */
class SupportedRaidLevelsConverterTest {

	private final SupportedRaidLevelsConverter converter = new SupportedRaidLevelsConverter();

	@Test
	@DisplayName("왕복 — VO → CSV → VO 가 동일 집합으로 복원 (선언 순 정렬)")
	void roundTrip_preservesSet() {
		SupportedRaidLevels original = SupportedRaidLevels.of(
				List.of(RaidLevel.RAID5, RaidLevel.RAID0));

		String column = converter.convertToDatabaseColumn(original);
		SupportedRaidLevels restored = converter.convertToEntityAttribute(column);

		assertThat(column).isEqualTo("RAID0,RAID5");
		assertThat(restored).isEqualTo(original);
	}

	@Test
	@DisplayName("convertToEntityAttribute — 미등록 토큰 → 명시 예외 (silent 흡수 금지)")
	void fromDb_rejectsUnknownToken() {
		assertThatThrownBy(() -> converter.convertToEntityAttribute("RAID0,RAID7"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RAID7");
	}

	@Test
	@DisplayName("convertToEntityAttribute — null/빈 컬럼 → 정합성 위반 예외 (NOT NULL + 최소 1개 계약)")
	void fromDb_rejectsBlank() {
		assertThatThrownBy(() -> converter.convertToEntityAttribute(""))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> converter.convertToEntityAttribute(null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("convertToDatabaseColumn — null 속성 → 최후 안전망 예외")
	void toDb_rejectsNullAttribute() {
		assertThatThrownBy(() -> converter.convertToDatabaseColumn(null))
				.isInstanceOf(IllegalStateException.class);
	}
}
