package com.example.serverprovision.global.orphan.entity;

import com.example.serverprovision.global.orphan.OrphanRecoveryPayload;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code orphan_quarantine.payload} (JSON 컬럼) ↔ {@link OrphanRecoveryPayload} 의 polymorphic 매핑.
 *
 * <p>{@link OrphanRecoveryPayload} 가 {@code @JsonTypeInfo(Id.CLASS)} 로 정의되어 있어 type id(클래스명)를 통해
 * 도메인별 구현체(예: {@code IsoRecoveryPayload})가 자동 deserialize 된다 — global 이 구현체를 알 필요가 없다.</p>
 *
 * <p>Jackson 3 — 어노테이션은 {@code com.fasterxml.jackson.annotation.*}, 런타임은 {@code tools.jackson.*}
 * (CLAUDE.md §기술 스택). {@code PurgeLogDetailsConverter} 와 동일 패턴.</p>
 */
@Converter(autoApply = false)
public class OrphanRecoveryPayloadConverter implements AttributeConverter<OrphanRecoveryPayload, String> {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	@Override
	public String convertToDatabaseColumn(OrphanRecoveryPayload attribute) {
		if (attribute == null) {
			return null;
		}
		return MAPPER.writeValueAsString(attribute);
	}

	@Override
	public OrphanRecoveryPayload convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		return MAPPER.readValue(dbData, OrphanRecoveryPayload.class);
	}
}
