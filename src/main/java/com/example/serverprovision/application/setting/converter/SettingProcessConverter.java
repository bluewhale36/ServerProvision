package com.example.serverprovision.application.setting.converter;

import com.example.serverprovision.application.setting.model.SettingProcess;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Converter(autoApply = true)
@RequiredArgsConstructor
public class SettingProcessConverter implements AttributeConverter<SettingProcess, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(SettingProcess attribute) {
        if (attribute == null) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize SettingProcess", e);
        }
    }

    @Override
    public SettingProcess convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return null;
        try {
            return objectMapper.readValue(dbData, SettingProcess.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize SettingProcess", e);
        }
    }
}
