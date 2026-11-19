package com.example.serverprovision.global.systemsetting.converter;

import com.example.serverprovision.global.systemsetting.vo.SystemSettingValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link SystemSettingValue} ↔ JSON 컬럼. 역직렬화가 값 VO 생성자를 거치므로 DB 에서 직접 고친 값도
 * 규칙을 어기면 읽히지 않는다(데이터 손상 = 500). 선례: {@code ProcessPayloadConverter}.
 */
@Converter(autoApply = false)
@RequiredArgsConstructor
public class SystemSettingValueConverter implements AttributeConverter<SystemSettingValue, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(SystemSettingValue attribute) {
        return attribute == null ? null : objectMapper.writeValueAsString(attribute);
    }

    @Override
    public SystemSettingValue convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return objectMapper.readValue(dbData, SystemSettingValue.class);
    }
}
