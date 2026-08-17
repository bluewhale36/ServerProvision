package com.example.serverprovision.management.raidcard.vo;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link CacheCapacity} ↔ {@code cache_capacity_gb INT} 컬럼 왕복. 0 = 캐시 없음.
 */
@Converter(autoApply = false)
public class CacheCapacityConverter implements AttributeConverter<CacheCapacity, Integer> {

	@Override
	public Integer convertToDatabaseColumn(CacheCapacity attribute) {
		if (attribute == null) {
			// 컬럼 NOT NULL + 등록 필수 입력 — 정상 경로에서 도달 불가한 최후 안전망.
			throw new IllegalStateException("cache_capacity_gb 는 null 일 수 없습니다.");
		}
		return attribute.gigabytes();
	}

	@Override
	public CacheCapacity convertToEntityAttribute(Integer dbData) {
		if (dbData == null) {
			throw new IllegalStateException("cache_capacity_gb 컬럼이 비어 있습니다 — 데이터 정합성 위반.");
		}
		return CacheCapacity.ofGigabytes(dbData);
	}
}
