package com.example.serverprovision.global.trash.entity;

import com.example.serverprovision.global.trash.PurgeLogDetails;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * S5-2-4 — purge_log.details (JSON 컬럼) ↔ {@link PurgeLogDetails} 의 polymorphic 매핑.
 *
 * <p>{@link PurgeLogDetails} 가 sealed interface + Jackson {@code @JsonTypeInfo} 로 정의되어 있어
 * type discriminator ("SUCCESS" / "FAILED") 를 통해 Success / Failed record 가 자동 deserialize.</p>
 *
 * <p>Jackson 3 사용 — 어노테이션은 {@code com.fasterxml.jackson.annotation.*}, 런타임은
 * {@code tools.jackson.*} (CLAUDE.md §기술 스택).</p>
 */
@Converter(autoApply = false)
public class PurgeLogDetailsConverter implements AttributeConverter<PurgeLogDetails, String> {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	@Override
	public String convertToDatabaseColumn(PurgeLogDetails attribute) {
		if (attribute == null) {
			return null;
		}
		return MAPPER.writeValueAsString(attribute);
	}

	@Override
	public PurgeLogDetails convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		return MAPPER.readValue(dbData, PurgeLogDetails.class);
	}
}
