package com.example.serverprovision.management.raidcard.vo;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link SupportedRaidLevels} ↔ CSV 컬럼({@code "RAID0,RAID1"}) 왕복.
 *
 * <p>enum 집합을 컬럼 하나에 저장하는 형태는 {@code OwnedPhasesConverter} 선례를 따른다. 다만 그쪽이
 * 별도 안정 코드 표를 두는 것과 달리 여기는 {@code RaidLevel.name()} 을 그대로 쓴다 — RAID0/RAID5 같은
 * 레벨명은 업계 표준 명칭이라 rename 유인이 없고, 표가 없는 쪽이 단순하기 때문이다. 미등록 토큰
 * 저장본은 silent 흡수하지 않고 명시 예외로 거절한다.</p>
 */
@Converter(autoApply = false)
public class SupportedRaidLevelsConverter implements AttributeConverter<SupportedRaidLevels, String> {

	@Override
	public String convertToDatabaseColumn(SupportedRaidLevels attribute) {
		if (attribute == null) {
			// 컬럼 NOT NULL + 엔티티 invariant(등록 필수 입력) — 정상 경로에서 도달 불가한 최후 안전망.
			throw new IllegalStateException("supported_raid_levels 는 null 일 수 없습니다.");
		}
		return attribute.asSet().stream().map(Enum::name).collect(Collectors.joining(","));
	}

	@Override
	public SupportedRaidLevels convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			throw new IllegalStateException("supported_raid_levels 컬럼이 비어 있습니다 — 데이터 정합성 위반.");
		}
		List<RaidLevel> levels = new ArrayList<>();
		for (String token : dbData.split(",")) {
			String name = token.trim();
			if (name.isEmpty()) {
				continue;
			}
			try {
				levels.add(RaidLevel.valueOf(name));
			} catch (IllegalArgumentException e) {
				throw new IllegalStateException("supported_raid_levels 에 알 수 없는 레벨이 있습니다 : " + name, e);
			}
		}
		return SupportedRaidLevels.of(levels);
	}
}
