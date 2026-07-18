package com.example.serverprovision.execution.converter;

import com.example.serverprovision.execution.vo.GuestToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link GuestToken} ↔ DB 컬럼(32자 문자열) 매핑 — MacAddressConverter 선례와 동형.
 */
@Converter(autoApply = false)
public class GuestTokenConverter implements AttributeConverter<GuestToken, String> {

    @Override
    public String convertToDatabaseColumn(GuestToken attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public GuestToken convertToEntityAttribute(String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : new GuestToken(dbData);
    }
}
